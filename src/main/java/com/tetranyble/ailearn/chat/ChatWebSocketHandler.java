package com.tetranyble.ailearn.chat;

import com.tetranyble.ailearn.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final int REPLAY_LIMIT = 500;

    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final ChatRunRepository runs;
    private final ChatEventStore eventStore;
    private final ChatLiveEventHub liveEvents;
    private final int sendTimeLimit;
    private final int bufferSizeLimit;
    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(
            ObjectMapper objectMapper,
            ChatService chatService,
            ChatRunRepository runs,
            ChatEventStore eventStore,
            ChatLiveEventHub liveEvents,
            @Value("${app.chat.websocket.send-time-limit}") int sendTimeLimit,
            @Value("${app.chat.websocket.buffer-size-limit}") int bufferSizeLimit
    ) {
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.runs = runs;
        this.eventStore = eventStore;
        this.liveEvents = liveEvents;
        this.sendTimeLimit = sendTimeLimit;
        this.bufferSizeLimit = bufferSizeLimit;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        Connection connection = new Connection(new ConcurrentWebSocketSessionDecorator(
                session,
                sendTimeLimit,
                bufferSizeLimit
        ));
        connections.put(session.getId(), connection);
        connection.send(ChatWebSocketFrame.of("ready", Map.of(
                "sessionId", session.getId(),
                "protocolVersion", 1
        )));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Connection connection = connections.get(session.getId());
        if (connection == null) {
            return;
        }
        String requestId = java.util.UUID.randomUUID().toString();
        try {
            ChatWebSocketCommand command = objectMapper.readValue(
                    message.getPayload(),
                    ChatWebSocketCommand.class
            );
            requestId = blankToNull(command.requestId()) == null
                    ? requestId
                    : command.requestId();
            try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
                handle(connection, command, requestId);
            }
        } catch (IllegalArgumentException exception) {
            connection.send(ChatWebSocketFrame.error(
                    requestId,
                    "invalid_command",
                    exception.getMessage()
            ));
        } catch (ResourceNotFoundException exception) {
            connection.send(ChatWebSocketFrame.error(requestId, "not_found", exception.getMessage()));
        } catch (RuntimeException exception) {
            log.warn("WebSocket chat command failed for session {}", session.getId(), exception);
            connection.send(ChatWebSocketFrame.error(
                    requestId,
                    "command_failed",
                    "The chat command could not be processed."
            ));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Connection connection = connections.remove(session.getId());
        if (connection != null) {
            connection.close();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Chat WebSocket transport closed for session {}", session.getId(), exception);
        Connection connection = connections.remove(session.getId());
        if (connection != null) {
            connection.close();
        }
    }

    private void handle(
            Connection connection,
            ChatWebSocketCommand command,
            String requestId
    ) {
        String type = require(command.type(), "type").toLowerCase();
        switch (type) {
            case "subscribe" -> subscribe(
                    connection,
                    require(command.conversationId(), "conversationId"),
                    require(command.runId(), "runId"),
                    command.after(),
                    requestId
            );
            case "unsubscribe" -> {
                connection.unsubscribe(require(command.runId(), "runId"));
                connection.send(ChatWebSocketFrame.of("unsubscribed", requestId, Map.of(
                        "runId", command.runId()
                )));
            }
            case "message" -> submit(connection, command, requestId);
            case "cancel" -> cancel(connection, command, requestId);
            case "ping" -> connection.send(ChatWebSocketFrame.of("pong", requestId, Map.of()));
            default -> throw new IllegalArgumentException("Unsupported command type: " + type);
        }
    }

    private void submit(
            Connection connection,
            ChatWebSocketCommand command,
            String requestId
    ) {
        String message = require(command.message(), "message").trim();
        if (message.length() > 4_000) {
            throw new IllegalArgumentException("message must not exceed 4000 characters");
        }
        ChatSubmissionResponse submission;
        if (command.conversationId() == null || command.conversationId().isBlank()) {
            if (command.replyToId() != null) {
                throw new IllegalArgumentException(
                        "replyToId cannot be used when starting a conversation"
                );
            }
            submission = chatService.startConversation(message);
        } else {
            submission = chatService.submit(
                    command.conversationId(),
                    message,
                    blankToNull(command.idempotencyKey()),
                    blankToNull(command.replyToId()),
                    parseMode(command.mode())
            );
        }
        connection.send(ChatWebSocketFrame.of("accepted", requestId, submission));
        subscribe(
                connection,
                submission.conversationId(),
                submission.run().id(),
                null,
                requestId
        );
    }

    private void cancel(
            Connection connection,
            ChatWebSocketCommand command,
            String requestId
    ) {
        String conversationId = require(command.conversationId(), "conversationId");
        ChatCancellationResponse result = command.runId() == null
                ? chatService.cancel(conversationId)
                : chatService.cancel(conversationId, command.runId());
        connection.send(ChatWebSocketFrame.of("cancel_accepted", requestId, result));
    }

    private void subscribe(
            Connection connection,
            String conversationId,
            String runId,
            String after,
            String requestId
    ) {
        ChatStreamEvent snapshot = runs.findStreamState(conversationId, runId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatRun", runId));
        connection.subscribe(runId);
        List<ChatStreamEvent> replay = eventStore.replay(runId, blankToNull(after), REPLAY_LIMIT);
        replay.forEach(connection::event);
        if (replay.isEmpty() || !sameState(replay.getLast(), snapshot)) {
            connection.event(snapshot);
        }
        connection.send(ChatWebSocketFrame.of("subscribed", requestId, Map.of(
                "conversationId", conversationId,
                "runId", runId
        )));
    }

    private boolean sameState(ChatStreamEvent first, ChatStreamEvent second) {
        return first.status().equals(second.status())
                && java.util.Objects.equals(first.content(), second.content())
                && java.util.Objects.equals(first.cancelReason(), second.cancelReason())
                && java.util.Objects.equals(first.errorCode(), second.errorCode());
    }

    private ChatMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return ChatMode.QUEUE;
        }
        try {
            return ChatMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("mode must be queue or interrupt");
        }
    }

    private String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private final class Connection {

        private final WebSocketSession session;
        private final Map<String, AutoCloseable> subscriptions = new HashMap<>();
        private final Map<String, Set<String>> delivered = new HashMap<>();

        private Connection(WebSocketSession session) {
            this.session = session;
        }

        synchronized void subscribe(String runId) {
            if (subscriptions.containsKey(runId)) {
                return;
            }
            subscriptions.put(runId, liveEvents.subscribe(runId, this::event));
        }

        synchronized void unsubscribe(String runId) {
            AutoCloseable registration = subscriptions.remove(runId);
            delivered.remove(runId);
            closeRegistration(registration);
        }

        synchronized void event(ChatStreamEvent event) {
            Set<String> eventIds = delivered.computeIfAbsent(
                    event.runId(),
                    ignored -> new HashSet<>()
            );
            if (event.eventId() != null && !eventIds.add(event.eventId())) {
                return;
            }
            send(ChatWebSocketFrame.of("chat", event.withSource(null)));
        }

        synchronized void send(ChatWebSocketFrame frame) {
            if (!session.isOpen()) {
                return;
            }
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
            } catch (IOException | RuntimeException exception) {
                log.debug("Unable to send chat WebSocket frame to {}", session.getId(), exception);
                close();
            }
        }

        synchronized void close() {
            subscriptions.values().forEach(this::closeRegistration);
            subscriptions.clear();
            delivered.clear();
            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.SERVER_ERROR);
                }
            } catch (IOException ignored) {
                // The network connection may already be gone.
            }
        }

        private void closeRegistration(AutoCloseable registration) {
            if (registration == null) {
                return;
            }
            try {
                registration.close();
            } catch (Exception ignored) {
                // Subscription cleanup is best effort during disconnect.
            }
        }
    }
}
