package com.tetranyble.ailearn.chat;

public record EnqueuedChatRun(
        ChatSubmissionResponse submission,
        String runToCancelId
) {
}
