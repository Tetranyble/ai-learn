package com.tetranyble.ailearn.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record ChatRequest(
        @NotBlank(message = "message is required")
        @Size(max = 4_000, message = "message must not exceed 4000 characters")
        String message,

        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "replyToId must be a UUID"
        )
        String replyToId,

        ChatMode mode
) {
    public ChatRequest(String message) {
        this(message, null, ChatMode.QUEUE);
    }

    public ChatMode effectiveMode() {
        return mode == null ? ChatMode.QUEUE : mode;
    }
}
