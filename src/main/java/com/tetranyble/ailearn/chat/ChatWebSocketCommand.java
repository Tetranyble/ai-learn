package com.tetranyble.ailearn.chat;

record ChatWebSocketCommand(
        String type,
        String requestId,
        String conversationId,
        String runId,
        String message,
        String mode,
        String replyToId,
        String idempotencyKey,
        String after
) {
}
