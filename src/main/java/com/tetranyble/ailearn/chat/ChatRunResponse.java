package com.tetranyble.ailearn.chat;

import java.time.Instant;

public record ChatRunResponse(
        String id,
        String conversationId,
        String userMessageId,
        String assistantMessageId,
        String mode,
        String status,
        String cancelReason,
        Instant cancellationRequestedAt,
        String errorCode,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
    public static ChatRunResponse from(ChatRun run) {
        return new ChatRunResponse(
                run.getId(),
                run.getConversationId(),
                run.getUserMessageId(),
                run.getAssistantMessageId(),
                run.getMode().name().toLowerCase(),
                run.getStatus().name().toLowerCase(),
                run.getCancelReason() == null
                        ? null
                        : run.getCancelReason().name().toLowerCase(),
                run.getCancellationRequestedAt(),
                run.getErrorCode(),
                run.getCreatedAt(),
                run.getStartedAt(),
                run.getCompletedAt()
        );
    }
}
