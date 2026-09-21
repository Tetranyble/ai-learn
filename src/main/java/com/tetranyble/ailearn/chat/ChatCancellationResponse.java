package com.tetranyble.ailearn.chat;

public record ChatCancellationResponse(
        String conversationId,
        boolean cancellationRequested,
        ChatRunResponse run
) {
}
