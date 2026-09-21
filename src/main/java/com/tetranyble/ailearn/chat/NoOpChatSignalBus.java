package com.tetranyble.ailearn.chat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.chat.redis.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoOpChatSignalBus implements ChatSignalBus {

    @Override
    public void publishQueued(String conversationId) {
    }

    @Override
    public void publishCancellation(String runId, ChatCancelReason reason) {
    }

    @Override
    public void publishStreamEvent(ChatStreamEvent event) {
    }
}
