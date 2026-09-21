package com.tetranyble.ailearn.chat;

import jakarta.validation.Validation;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatResourceTests {

    @Test
    void acceptsAChatRun() {
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
        ChatSubmissionResponse expected = new ChatSubmissionResponse(
                "conversation-id",
                new ChatRunResponse(
                        "run-id",
                        "conversation-id",
                        "user-message-id",
                        "message-id",
                        "queue",
                        "queued",
                        null,
                        null,
                        null,
                        Instant.parse("2026-09-21T11:59:59Z"),
                        null,
                        null
                ),
                userMessage,
                assistantMessage
        );
        when(chatService.startConversation("Hello")).thenReturn(expected);
        ChatResource resource = new ChatResource(
                chatService,
                mock(ConversationService.class),
                mock(ChatRunSseService.class)
        );

        Response response = resource.chat(new ChatRequest("  Hello  "));

        assertThat(response.getStatus()).isEqualTo(202);
        assertThat(response.getEntity()).isEqualTo(expected);
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
    void forwardsInterruptModeToTheService() {
        ChatService chatService = mock(ChatService.class);
        ChatResource resource = new ChatResource(
                chatService,
                mock(ConversationService.class),
                mock(ChatRunSseService.class)
        );
        String conversationId = "01f44d61-a0cf-4fdd-8415-ca30898926b3";
        ChatRequest request = new ChatRequest("Additional context", null, ChatMode.INTERRUPT);

        resource.continueChat(conversationId, "request-1", request);

        verify(chatService).submit(
                conversationId,
                "Additional context",
                "request-1",
                null,
                ChatMode.INTERRUPT
        );
    }

    @Test
    void delegatesRunStreamingToTheSseService() {
        ChatRunSseService streamService = mock(ChatRunSseService.class);
        ChatResource resource = new ChatResource(
                mock(ChatService.class),
                mock(ConversationService.class),
                streamService
        );
        SseEventSink sink = mock(SseEventSink.class);
        Sse sse = mock(Sse.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        resource.streamRun("conversation-id", "run-id", sink, sse, response);

        verify(response).setHeader("Cache-Control", "no-cache, no-transform");
        verify(response).setHeader("X-Accel-Buffering", "no");
        verify(streamService).stream("conversation-id", "run-id", sink, sse);
    }
}
