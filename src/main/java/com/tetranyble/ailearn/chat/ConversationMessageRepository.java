package com.tetranyble.ailearn.chat;

import com.tetranyble.ailearn.exception.ResourceNotFoundException;
import com.tetranyble.ailearn.validation.RequestValidationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class ConversationMessageRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<ConversationMessage> findAfter(
            String conversationId,
            long afterSequence,
            int limit
    ) {
        return entityManager.createQuery("""
                        select message
                        from ConversationMessage message
                        where message.conversation.id = :conversationId
                          and message.sequence > :afterSequence
                        order by message.sequence asc
                        """, ConversationMessage.class)
                .setParameter("conversationId", conversationId)
                .setParameter("afterSequence", afterSequence)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<ConversationMessage> findLatest(String conversationId, int limit) {
        List<ConversationMessage> messages = new ArrayList<>(
                entityManager.createQuery("""
                                select message
                                from ConversationMessage message
                                where message.conversation.id = :conversationId
                                order by message.sequence desc
                                """, ConversationMessage.class)
                        .setParameter("conversationId", conversationId)
                        .setMaxResults(limit)
                        .getResultList()
        );
        Collections.reverse(messages);
        return messages;
    }

    public Optional<ConversationTurn> findTurnByIdempotencyKey(
            String conversationId,
            String idempotencyKey
    ) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }

        Optional<ConversationMessage> userMessage = entityManager.createQuery("""
                        select userMessage
                        from ConversationMessage userMessage
                        where userMessage.conversation.id = :conversationId
                          and userMessage.idempotencyKey = :idempotencyKey
                        """, ConversationMessage.class)
                .setParameter("conversationId", conversationId)
                .setParameter("idempotencyKey", idempotencyKey)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();

        if (userMessage.isEmpty()) {
            return Optional.empty();
        }

        Optional<ConversationMessage> assistantMessage = entityManager.createQuery("""
                        select assistant
                        from ConversationMessage assistant
                        where assistant.conversation.id = :conversationId
                          and assistant.sequence = :sequence
                          and assistant.role = :assistantRole
                        """, ConversationMessage.class)
                .setParameter("conversationId", conversationId)
                .setParameter("sequence", userMessage.get().getSequence() + 1)
                .setParameter("assistantRole", MessageRole.ASSISTANT)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();

        return assistantMessage.map(assistant ->
                new ConversationTurn(userMessage.get(), assistant)
        );
    }

    @Transactional
    public ConversationTurn appendTurn(
            String conversationId,
            String userMessage,
            String assistantMessage,
            String idempotencyKey,
            String replyToId
    ) {
        Conversation conversation = entityManager.find(
                Conversation.class,
                conversationId,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (conversation == null) {
            throw new ResourceNotFoundException("Conversation", conversationId);
        }

        ConversationMessage replyTo = findReplyTarget(conversationId, replyToId);

        ConversationMessage user = new ConversationMessage(
                conversation,
                conversation.takeNextSequence(),
                MessageRole.USER,
                userMessage,
                idempotencyKey,
                replyTo
        );
        entityManager.persist(user);

        ConversationMessage assistant = new ConversationMessage(
                conversation,
                conversation.takeNextSequence(),
                MessageRole.ASSISTANT,
                assistantMessage,
                null,
                user
        );

        entityManager.persist(assistant);
        conversation.recordMessageAt(Instant.now(), userMessage);
        entityManager.flush();

        return new ConversationTurn(user, assistant);
    }

    public void validateReplyTarget(String conversationId, String replyToId) {
        findReplyTarget(conversationId, replyToId);
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
}
