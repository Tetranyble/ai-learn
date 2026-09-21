package com.tetranyble.ailearn.chat;

import java.util.List;

public record ChatStateResponse(
        ConversationResponse conversation,
        List<MessageResponse> messages,
        List<ChatRunResponse> runs,
        Long nextCursor,
        boolean hasMore
) {
}
