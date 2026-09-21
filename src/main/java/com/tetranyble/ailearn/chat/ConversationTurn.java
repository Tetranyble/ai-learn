package com.tetranyble.ailearn.chat;

public record ConversationTurn(
        ConversationMessage userMessage,
        ConversationMessage assistantMessage
) {
}
