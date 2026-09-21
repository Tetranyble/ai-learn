package com.tetranyble.ailearn.chat;

public record ChatSubmissionResponse(
        String conversationId,
        ChatRunResponse run,
        MessageResponse userMessage,
        MessageResponse assistantMessage
) {
    public static ChatSubmissionResponse from(ChatRun run) {
        return new ChatSubmissionResponse(
                run.getConversationId(),
                ChatRunResponse.from(run),
                MessageResponse.from(run.getUserMessage()),
                MessageResponse.from(run.getAssistantMessage())
        );
    }
}
