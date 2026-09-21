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

@SpringBootTest(properties = "langchain4j.open-ai.chat-model.api-key=test-key")
@ActiveProfiles("test")
class ConversationPersistenceTests {

    @Autowired
    private ConversationRepository conversations;

    @Autowired
    private ConversationMessageRepository messages;

    @Autowired
    private JpaChatMemoryStore memoryStore;

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
}
