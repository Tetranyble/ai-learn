package com.tetranyble.ailearn.chat;

public record ChatRunWork(
        String runId,
        String conversationId,
        String assistantMessageId,
        String userMessage
) {
}
