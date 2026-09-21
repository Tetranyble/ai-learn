package com.tetranyble.ailearn.chat;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 160, message = "title must not exceed 160 characters")
        String title
) {
}
