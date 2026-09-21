package com.tetranyble.ailearn.chat;

import java.time.Instant;

public record MessageResponse(
        String id,
        long sequence,
        String role,
        String content,
        String replyToId,
        String runId,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public MessageResponse(
            String id,
            long sequence,
            String role,
            String content,
            String replyToId,
            Instant createdAt
    ) {
        this(
                id,
                sequence,
                role,
                content,
                replyToId,
                null,
                "completed",
                createdAt,
                createdAt
        );
    }

    public static MessageResponse from(ConversationMessage message) {
        return new MessageResponse(
                message.getId(),
                message.getSequence(),
                message.getRole().name().toLowerCase(),
                message.getContent(),
                message.getReplyToId(),
                message.getRunId(),
                message.getStatus().name().toLowerCase(),
                message.getCreatedAt(),
                message.getUpdatedAt()
        );
    }
}
