package com.tetranyble.ailearn.chat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public record ChatStreamEvent(
        String eventId,
        String cursor,
        String type,
        String conversationId,
        String runId,
        String messageId,
        String content,
        String status,
        String cancelReason,
        String errorCode,
        Instant occurredAt,
        String sourceId
) {
    public static ChatStreamEvent snapshot(
            ChatRunResponse run,
            MessageResponse message
    ) {
        return new ChatStreamEvent(
                UUID.randomUUID().toString(),
                null,
                "snapshot",
                run.conversationId(),
                run.id(),
                message.id(),
                message.content(),
                run.status(),
                run.cancelReason(),
                run.errorCode(),
                Instant.now(),
                null
        );
    }

    public static ChatStreamEvent update(
            String conversationId,
            String runId,
            String messageId,
            String content,
            ChatRunStatus status,
            ChatCancelReason cancelReason,
            String errorCode
    ) {
        return new ChatStreamEvent(
                UUID.randomUUID().toString(),
                null,
                status.isTerminal() ? "terminal" : "update",
                conversationId,
                runId,
                messageId,
                content,
                status.name().toLowerCase(),
                cancelReason == null ? null : cancelReason.name().toLowerCase(),
                errorCode,
                Instant.now(),
                null
        );
    }

    ChatStreamEvent withSource(String sourceId) {
        return new ChatStreamEvent(
                eventId,
                cursor,
                type,
                conversationId,
                runId,
                messageId,
                content,
                status,
                cancelReason,
                errorCode,
                occurredAt,
                sourceId
        );
    }

    ChatStreamEvent withCursor(String cursor) {
        return new ChatStreamEvent(
                eventId,
                cursor,
                type,
                conversationId,
                runId,
                messageId,
                content,
                status,
                cancelReason,
                errorCode,
                occurredAt,
                sourceId
        );
    }

    String encode() {
        return String.join("|",
                encode(eventId),
                encode(cursor),
                encode(type),
                encode(conversationId),
                encode(runId),
                encode(messageId),
                encode(content),
                encode(status),
                encode(cancelReason),
                encode(errorCode),
                encode(occurredAt.toString()),
                encode(sourceId)
        );
    }

    static ChatStreamEvent decode(String payload) {
        String[] fields = payload.split("\\|", -1);
        if (fields.length != 12) {
            throw new IllegalArgumentException("Invalid chat stream event");
        }
        return new ChatStreamEvent(
                decodeField(fields[0]),
                decodeField(fields[1]),
                decodeField(fields[2]),
                decodeField(fields[3]),
                decodeField(fields[4]),
                decodeField(fields[5]),
                decodeField(fields[6]),
                decodeField(fields[7]),
                decodeField(fields[8]),
                decodeField(fields[9]),
                Instant.parse(decodeField(fields[10])),
                decodeField(fields[11])
        );
    }

    public boolean terminal() {
        return ChatRunStatus.valueOf(status.toUpperCase()).isTerminal();
    }

    private static String encode(String value) {
        if (value == null) {
            return "";
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String decodeField(String value) {
        if (value.isEmpty()) {
            return null;
        }
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
