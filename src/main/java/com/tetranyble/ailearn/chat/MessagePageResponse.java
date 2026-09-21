package com.tetranyble.ailearn.chat;

import java.util.List;

public record MessagePageResponse(
        List<MessageResponse> items,
        Long nextCursor,
        boolean hasMore
) {
}
