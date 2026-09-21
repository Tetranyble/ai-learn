package com.tetranyble.ailearn.chat;

import java.time.Instant;

public record ConversationResponse(
        String id,
        String title,
        Instant createdAt,
        Instant updatedAt,
        Instant lastMessageAt
) {
    public static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                conversation.getLastMessageAt()
        );
    }
}
