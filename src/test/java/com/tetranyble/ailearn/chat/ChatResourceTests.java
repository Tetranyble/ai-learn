package com.tetranyble.ailearn.chat;

import jakarta.validation.Validation;
import com.tetranyble.ailearn.exception.ChatProviderException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatResourceTests {

    @Test
    void returnsTheAssistantReply() {
        ChatService chatService = mock(ChatService.class);
        MessageResponse assistantMessage = new MessageResponse(
                "message-id",
                2,
                "assistant",
                "Reply to: Hello",
                "user-message-id",
                Instant.parse("2026-09-21T12:00:00Z")
        );
        MessageResponse userMessage = new MessageResponse(
                "user-message-id",
                1,
                "user",
                "Hello",
                null,
                Instant.parse("2026-09-21T11:59:59Z")
        );
        ChatResponse expected = new ChatResponse(
                "conversation-id",
                userMessage,
                assistantMessage
        );
        when(chatService.startConversation("Hello")).thenReturn(expected);
        ChatResource resource = new ChatResource(
                chatService,
                mock(ConversationService.class)
        );

        ChatResponse response = resource.chat(new ChatRequest("  Hello  "));

        assertThat(response).isEqualTo(expected);
    }

    @Test
    void rejectsBlankMessages() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var violations = validatorFactory.getValidator()
                    .validate(new ChatRequest("  "));

            assertThat(violations)
                    .extracting(violation -> violation.getMessage())
                    .containsExactly("message is required");
        }
    }

    @Test
    void hidesProviderFailureDetails() {
        ChatService chatService = mock(ChatService.class);
        ChatProviderException failure = new ChatProviderException(
                new IllegalStateException("sensitive provider details")
        );
        when(chatService.startConversation("Hello")).thenThrow(failure);
        ChatResource resource = new ChatResource(
                chatService,
                mock(ConversationService.class)
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> resource.chat(new ChatRequest("Hello"))
                )
                .isInstanceOf(ChatProviderException.class)
                .hasMessage("The chat service is temporarily unavailable.")
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}
