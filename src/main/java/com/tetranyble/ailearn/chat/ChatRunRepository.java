package com.tetranyble.ailearn.chat;

import com.tetranyble.ailearn.exception.ResourceNotFoundException;
import com.tetranyble.ailearn.validation.RequestValidationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class ChatRunRepository {

    private static final Duration RUN_LEASE = Duration.ofMinutes(2);

    @PersistenceContext
    private EntityManager entityManager;

    private final ChatOutboxRepository outbox;

    public ChatRunRepository(ChatOutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Transactional
    public EnqueuedChatRun enqueue(
            String conversationId,
            String content,
            String idempotencyKey,
            String replyToId,
            ChatMode mode
    ) {
        if (idempotencyKey != null) {
            Optional<ChatRun> existing = findByIdempotencyKey(conversationId, idempotencyKey);
            if (existing.isPresent()) {
                return new EnqueuedChatRun(ChatSubmissionResponse.from(existing.get()), null);
            }
        }

        Conversation conversation = entityManager.find(
                Conversation.class,
                conversationId,
                LockModeType.PESSIMISTIC_WRITE
        );
        if (conversation == null) {
            throw new ResourceNotFoundException("Conversation", conversationId);
        }

        ConversationMessage replyTo = findReplyTarget(conversationId, replyToId);
        String runId = UUID.randomUUID().toString();

        ConversationMessage user = new ConversationMessage(
                conversation,
                conversation.takeNextSequence(),
                MessageRole.USER,
                content,
                idempotencyKey,
                replyTo,
                runId,
                MessageStatus.QUEUED
        );
        entityManager.persist(user);

        ConversationMessage assistant = new ConversationMessage(
                conversation,
                conversation.takeNextSequence(),
                MessageRole.ASSISTANT,
                "",
                null,
                user,
                runId,
                MessageStatus.QUEUED
        );
        entityManager.persist(assistant);

        ChatRun run = new ChatRun(runId, conversation, user, assistant, mode);
        entityManager.persist(run);
        conversation.recordMessageAt(Instant.now(), content);
        appendEvent(run);

        String runToCancelId = null;
        if (mode == ChatMode.INTERRUPT) {
            Optional<ChatRun> active = findActive(conversationId);
            if (active.isPresent()) {
                active.get().requestCancellation(ChatCancelReason.STEERED);
                runToCancelId = active.get().getId();
            }
        }

        entityManager.flush();
        return new EnqueuedChatRun(ChatSubmissionResponse.from(run), runToCancelId);
    }

    @Transactional
    public Optional<ChatRunWork> claimNext(String conversationId, String workerId) {
        Conversation conversation = entityManager.find(
                Conversation.class,
                conversationId,
                LockModeType.PESSIMISTIC_WRITE
        );
        if (conversation == null) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        Optional<ChatRun> active = findActive(conversationId);
        if (active.isPresent()) {
            ChatRun current = active.get();
            if (current.getLeaseExpiresAt() == null
                    || current.getLeaseExpiresAt().isAfter(now)) {
                return Optional.empty();
            }

            current.recoverExpiredLease();
            current.getUserMessage().transitionTo(MessageStatus.QUEUED);
            current.getAssistantMessage().transitionTo(MessageStatus.QUEUED);
            current.getAssistantMessage().replaceContent("");
        }

        Optional<ChatRun> queued = entityManager.createQuery("""
                        select run
                        from ChatRun run
                        join fetch run.userMessage
                        join fetch run.assistantMessage
                        where run.conversation.id = :conversationId
                          and run.status = :status
                        order by run.createdAt asc
                        """, ChatRun.class)
                .setParameter("conversationId", conversationId)
                .setParameter("status", ChatRunStatus.QUEUED)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();

        if (queued.isEmpty()) {
            return Optional.empty();
        }

        ChatRun run = queued.get();
        run.start(workerId, now.plus(RUN_LEASE));
        run.getUserMessage().transitionTo(MessageStatus.PROCESSING);
        run.getAssistantMessage().transitionTo(MessageStatus.PROCESSING);
        appendEvent(run);
        entityManager.flush();

        return Optional.of(new ChatRunWork(
                run.getId(),
                run.getConversationId(),
                run.getAssistantMessageId(),
                run.getUserMessage().getContent()
        ));
    }

    @Transactional
    public Optional<ChatRunResponse> requestActiveCancellation(
            String conversationId,
            ChatCancelReason reason
    ) {
        Conversation conversation = entityManager.find(
                Conversation.class,
                conversationId,
                LockModeType.PESSIMISTIC_WRITE
        );
        if (conversation == null) {
            throw new ResourceNotFoundException("Conversation", conversationId);
        }

        Optional<ChatRun> active = findActive(conversationId);
        if (active.isPresent()) {
            active.get().requestCancellation(reason);
        } else {
            active = entityManager.createQuery("""
                            select run
                            from ChatRun run
                            join fetch run.userMessage
                            join fetch run.assistantMessage
                            where run.conversation.id = :conversationId
                              and run.status = :status
                            order by run.createdAt asc
                            """, ChatRun.class)
                    .setParameter("conversationId", conversationId)
                    .setParameter("status", ChatRunStatus.QUEUED)
                    .setMaxResults(1)
                    .getResultList()
                    .stream()
                    .findFirst();
            active.ifPresent(run -> {
                run.requestCancellation(reason);
                run.getUserMessage().transitionTo(MessageStatus.CANCELLED);
                run.getAssistantMessage().transitionTo(MessageStatus.CANCELLED);
                run.cancel(ChatRunStatus.CANCELLED);
                appendEvent(run);
            });
        }
        entityManager.flush();
        return active.map(ChatRunResponse::from);
    }

    @Transactional
    public Optional<ChatRunResponse> requestCancellation(
            String conversationId,
            String runId,
            ChatCancelReason reason
    ) {
        Conversation conversation = entityManager.find(
                Conversation.class,
                conversationId,
                LockModeType.PESSIMISTIC_WRITE
        );
        if (conversation == null) {
            throw new ResourceNotFoundException("Conversation", conversationId);
        }

        ChatRun run = entityManager.find(
                ChatRun.class,
                runId,
                LockModeType.PESSIMISTIC_WRITE
        );
        if (run == null || !run.getConversationId().equals(conversationId)) {
            throw new ResourceNotFoundException("ChatRun", runId);
        }
        if (run.getStatus().isTerminal()) {
            return Optional.empty();
        }

        run.requestCancellation(reason);
        if (run.getStatus() == ChatRunStatus.QUEUED) {
            run.getUserMessage().transitionTo(MessageStatus.CANCELLED);
            run.getAssistantMessage().transitionTo(MessageStatus.CANCELLED);
            run.cancel(ChatRunStatus.CANCELLED);
            appendEvent(run);
        }
        entityManager.flush();
        return Optional.of(ChatRunResponse.from(run));
    }

    public Optional<ChatCancelReason> cancellationReason(String runId) {
        ChatRun run = entityManager.find(ChatRun.class, runId);
        if (run == null || run.getCancellationRequestedAt() == null) {
            return Optional.empty();
        }
        return Optional.of(run.getCancelReason());
    }

    @Transactional
    public void updatePartial(
            String runId,
            String workerId,
            String content
    ) {
        ChatRun run = findOwnedProcessingRun(runId, workerId);
        if (run == null) {
            return;
        }
        run.getAssistantMessage().replaceContent(content);
        run.renew(workerId, Instant.now().plus(RUN_LEASE));
        appendEvent(run);
    }

    @Transactional
    public void complete(String runId, String workerId, String content) {
        ChatRun run = findOwnedProcessingRun(runId, workerId);
        if (run == null) {
            return;
        }
        run.getUserMessage().transitionTo(MessageStatus.COMPLETED);
        run.getAssistantMessage().replaceContent(content);
        run.getAssistantMessage().transitionTo(MessageStatus.COMPLETED);
        run.complete();
        appendEvent(run);
    }

    @Transactional
    public void cancel(
            String runId,
            String workerId,
            String partialContent,
            ChatCancelReason reason
    ) {
        ChatRun run = findOwnedProcessingRun(runId, workerId);
        if (run == null) {
            return;
        }
        ChatRunStatus runStatus = reason == ChatCancelReason.STEERED
                ? ChatRunStatus.INTERRUPTED
                : ChatRunStatus.CANCELLED;
        MessageStatus messageStatus = reason == ChatCancelReason.STEERED
                ? MessageStatus.INTERRUPTED
                : MessageStatus.CANCELLED;

        run.getUserMessage().transitionTo(MessageStatus.COMPLETED);
        run.getAssistantMessage().replaceContent(partialContent);
        run.getAssistantMessage().transitionTo(messageStatus);
        run.cancel(runStatus);
        appendEvent(run);
    }

    @Transactional
    public void fail(String runId, String workerId, String partialContent) {
        ChatRun run = findOwnedProcessingRun(runId, workerId);
        if (run == null) {
            return;
        }
        run.getUserMessage().transitionTo(MessageStatus.COMPLETED);
        run.getAssistantMessage().replaceContent(partialContent);
        run.getAssistantMessage().transitionTo(MessageStatus.FAILED);
        run.fail("chat_provider_error");
        appendEvent(run);
    }

    public List<String> findDispatchableConversationIds(int limit) {
        Instant now = Instant.now();
        return entityManager.createQuery("""
                        select distinct run.conversation.id
                        from ChatRun run
                        where run.status = :queued
                           or (run.status = :processing and run.leaseExpiresAt < :now)
                        order by run.conversation.id
                        """, String.class)
                .setParameter("queued", ChatRunStatus.QUEUED)
                .setParameter("processing", ChatRunStatus.PROCESSING)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<ChatRunResponse> findForConversation(String conversationId, int limit) {
        List<ChatRunResponse> found = new ArrayList<>(entityManager.createQuery("""
                        select run
                        from ChatRun run
                        join fetch run.userMessage
                        join fetch run.assistantMessage
                        where run.conversation.id = :conversationId
                        order by run.createdAt desc
                        """, ChatRun.class)
                .setParameter("conversationId", conversationId)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(ChatRunResponse::from)
                .toList());
        Collections.reverse(found);
        return found;
    }

    public Optional<ChatStreamEvent> findStreamState(
            String conversationId,
            String runId
    ) {
        return entityManager.createQuery("""
                        select run
                        from ChatRun run
                        join fetch run.userMessage
                        join fetch run.assistantMessage
                        where run.id = :runId
                          and run.conversation.id = :conversationId
                        """, ChatRun.class)
                .setParameter("runId", runId)
                .setParameter("conversationId", conversationId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .map(run -> ChatStreamEvent.snapshot(
                        ChatRunResponse.from(run),
                        MessageResponse.from(run.getAssistantMessage())
                ));
    }

    private Optional<ChatRun> findByIdempotencyKey(
            String conversationId,
            String idempotencyKey
    ) {
        return entityManager.createQuery("""
                        select run
                        from ChatRun run
                        join fetch run.userMessage userMessage
                        join fetch run.assistantMessage
                        where run.conversation.id = :conversationId
                          and userMessage.idempotencyKey = :idempotencyKey
                        """, ChatRun.class)
                .setParameter("conversationId", conversationId)
                .setParameter("idempotencyKey", idempotencyKey)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    private Optional<ChatRun> findActive(String conversationId) {
        return entityManager.createQuery("""
                        select run
                        from ChatRun run
                        join fetch run.userMessage
                        join fetch run.assistantMessage
                        where run.conversation.id = :conversationId
                          and run.status = :status
                        order by run.startedAt asc
                        """, ChatRun.class)
                .setParameter("conversationId", conversationId)
                .setParameter("status", ChatRunStatus.PROCESSING)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    private ChatRun findOwnedProcessingRun(String runId, String workerId) {
        ChatRun run = entityManager.find(
                ChatRun.class,
                runId,
                LockModeType.PESSIMISTIC_WRITE
        );
        if (run == null
                || run.getStatus() != ChatRunStatus.PROCESSING
                || !workerId.equals(run.getWorkerId())) {
            return null;
        }
        return run;
    }

    private ConversationMessage findReplyTarget(
            String conversationId,
            String replyToId
    ) {
        if (replyToId == null) {
            return null;
        }

        ConversationMessage replyTo = entityManager.find(
                ConversationMessage.class,
                replyToId
        );
        if (replyTo == null
                || !replyTo.getConversationId().equals(conversationId)) {
            throw RequestValidationException.forField(
                    "replyToId",
                    "replyToId must reference a message in this conversation"
            );
        }
        return replyTo;
    }

    private void appendEvent(ChatRun run) {
        outbox.append(ChatStreamEvent.update(
                run.getConversationId(),
                run.getId(),
                run.getAssistantMessageId(),
                run.getAssistantMessage().getContent(),
                run.getStatus(),
                run.getCancelReason(),
                run.getErrorCode()
        ));
    }
}
