package com.tetranyble.ailearn.chat;

public record ChatResponse(
        String conversationId,
        MessageResponse userMessage,
        MessageResponse assistantMessage
) {
}
