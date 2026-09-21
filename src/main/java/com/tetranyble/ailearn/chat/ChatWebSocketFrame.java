package com.tetranyble.ailearn.chat;

import java.time.Instant;

record ChatWebSocketFrame(
        String type,
        String requestId,
        Object data,
        ChatWebSocketError error,
        Instant timestamp
) {
    static ChatWebSocketFrame of(String type, Object data) {
        return of(type, null, data);
    }

    static ChatWebSocketFrame of(String type, String requestId, Object data) {
        return new ChatWebSocketFrame(type, requestId, data, null, Instant.now());
    }

    static ChatWebSocketFrame error(
            String requestId,
            String code,
            String message
    ) {
        return new ChatWebSocketFrame(
                "error",
                requestId,
                null,
                new ChatWebSocketError(code, message),
                Instant.now()
        );
    }
}

record ChatWebSocketError(String code, String message) {
}
