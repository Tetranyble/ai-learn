package com.tetranyble.ailearn.chat;

import com.tetranyble.ailearn.exception.ConversationBusyException;
import com.tetranyble.ailearn.validation.RequestValidationException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.chat.recovery-scan-interval=10m")
@ActiveProfiles("test")
class ConversationPersistenceTests {

    @Autowired
    private ConversationRepository conversations;

    @Autowired
    private ConversationMessageRepository messages;

    @Autowired
    private JpaChatMemoryStore memoryStore;

    @Autowired
    private ChatRunRepository runs;

    @Test
    void persistsTranscriptMemoryCursorAndProcessingLease() {
        Conversation conversation = conversations.save(new Conversation(null));

        String lease = conversations.acquireProcessingLease(conversation.getId());
        assertThatThrownBy(
                () -> conversations.acquireProcessingLease(conversation.getId())
        ).isInstanceOf(ConversationBusyException.class);
        conversations.releaseProcessingLease(conversation.getId(), lease);

        messages.appendTurn(
                conversation.getId(),
                "What is JPA?",
                "JPA is a Java persistence specification.",
                "message-1",
                null
        );

        var firstPage = messages.findAfter(conversation.getId(), 0, 1);
        var secondPage = messages.findAfter(
                conversation.getId(),
                firstPage.getFirst().getSequence(),
                10
        );

        assertThat(firstPage)
                .extracting(ConversationMessage::getRole)
                .containsExactly(MessageRole.USER);
        assertThat(secondPage)
                .extracting(ConversationMessage::getRole)
                .containsExactly(MessageRole.ASSISTANT);
        assertThat(messages.findTurnByIdempotencyKey(
                conversation.getId(),
                "message-1"
        )).isPresent();

        assertThat(memoryStore.getMessages(conversation.getId()))
                .containsExactly(
                        UserMessage.from("What is JPA?"),
                        AiMessage.from("JPA is a Java persistence specification.")
                );

        memoryStore.updateMessages(
                conversation.getId(),
                List.of(UserMessage.from("bounded context"))
        );

        assertThat(memoryStore.getMessages(conversation.getId()))
                .containsExactly(UserMessage.from("bounded context"));
    }

    @Test
    void persistsAndValidatesReplyRelationships() {
        Conversation conversation = conversations.save(new Conversation(null));
        ConversationTurn firstTurn = messages.appendTurn(
                conversation.getId(),
                "Explain repositories.",
                "Repositories isolate persistence operations.",
                "reply-test-1",
                null
        );

        ConversationTurn secondTurn = messages.appendTurn(
                conversation.getId(),
                "Can you expand on that?",
                "They keep persistence details outside resources and services.",
                "reply-test-2",
                firstTurn.assistantMessage().getId()
        );

        assertThat(firstTurn.assistantMessage().getReplyToId())
                .isEqualTo(firstTurn.userMessage().getId());
        assertThat(secondTurn.userMessage().getReplyToId())
                .isEqualTo(firstTurn.assistantMessage().getId());
        assertThat(secondTurn.assistantMessage().getReplyToId())
                .isEqualTo(secondTurn.userMessage().getId());

        Conversation anotherConversation = conversations.save(new Conversation(null));

        assertThatThrownBy(() -> messages.validateReplyTarget(
                anotherConversation.getId(),
                firstTurn.userMessage().getId()
        )).isInstanceOf(RequestValidationException.class);
    }

    @Test
    void queuesInterruptsAndPersistsReloadableRunState() {
        Conversation conversation = conversations.save(new Conversation(null));

        EnqueuedChatRun first = runs.enqueue(
                conversation.getId(),
                "Draft an explanation.",
                "run-request-1",
                null,
                ChatMode.QUEUE
        );

        assertThat(first.submission().run().status()).isEqualTo("queued");
        assertThat(first.submission().userMessage().status()).isEqualTo("queued");
        assertThat(first.submission().assistantMessage().content()).isEmpty();

        ChatRunWork active = runs.claimNext(conversation.getId(), "worker-1")
                .orElseThrow();

        EnqueuedChatRun steering = runs.enqueue(
                conversation.getId(),
                "Use a practical example too.",
                "run-request-2",
                first.submission().assistantMessage().id(),
                ChatMode.INTERRUPT
        );

        assertThat(steering.runToCancelId()).isEqualTo(active.runId());
        assertThat(runs.cancellationReason(active.runId()))
                .contains(ChatCancelReason.STEERED);

        runs.updatePartial(active.runId(), "worker-1", "Partial answer");
        runs.cancel(
                active.runId(),
                "worker-1",
                "Partial answer",
                ChatCancelReason.STEERED
        );

        ChatRunWork next = runs.claimNext(conversation.getId(), "worker-1")
                .orElseThrow();
        assertThat(next.runId()).isEqualTo(steering.submission().run().id());

        assertThat(runs.findForConversation(conversation.getId(), 10))
                .extracting(ChatRunResponse::status)
                .containsExactly("interrupted", "processing");
        assertThat(messages.findAfter(conversation.getId(), 0, 10))
                .extracting(ConversationMessage::getStatus)
                .containsExactly(
                        MessageStatus.COMPLETED,
                        MessageStatus.INTERRUPTED,
                        MessageStatus.PROCESSING,
                        MessageStatus.PROCESSING
                );
    }

    @Test
    void cancelsAQueuedRunBeforeGenerationStarts() {
        Conversation conversation = conversations.save(new Conversation(null));
        EnqueuedChatRun queued = runs.enqueue(
                conversation.getId(),
                "This should not reach the model.",
                "cancel-before-start",
                null,
                ChatMode.QUEUE
        );

        ChatRunResponse cancelled = runs.requestCancellation(
                        conversation.getId(),
                        queued.submission().run().id(),
                        ChatCancelReason.USER
                )
                .orElseThrow();

        assertThat(cancelled.id()).isEqualTo(queued.submission().run().id());
        assertThat(cancelled.status()).isEqualTo("cancelled");
        assertThat(messages.findAfter(conversation.getId(), 0, 10))
                .extracting(ConversationMessage::getStatus)
                .containsExactly(MessageStatus.CANCELLED, MessageStatus.CANCELLED);
    }
}
