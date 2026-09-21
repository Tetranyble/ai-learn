package com.tetranyble.ailearn.chat;

import java.time.Instant;

public record MessageResponse(
        String id,
        long sequence,
        String role,
        String content,
        String replyToId,
        Instant createdAt
) {
    public static MessageResponse from(ConversationMessage message) {
        return new MessageResponse(
                message.getId(),
                message.getSequence(),
                message.getRole().name().toLowerCase(),
                message.getContent(),
                message.getReplyToId(),
                message.getCreatedAt()
        );
    }
}
