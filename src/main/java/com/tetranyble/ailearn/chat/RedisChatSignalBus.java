package com.tetranyble.ailearn.chat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.chat.redis.enabled", havingValue = "true")
public class RedisChatSignalBus implements ChatSignalBus {

    static final String QUEUED_CHANNEL = "ai-learn:chat:queued";
    static final String CANCEL_CHANNEL = "ai-learn:chat:cancel";
    static final String STREAM_CHANNEL = "ai-learn:chat:stream";

    private final StringRedisTemplate redis;

    public RedisChatSignalBus(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void publishQueued(String conversationId) {
        redis.convertAndSend(QUEUED_CHANNEL, conversationId);
    }

    @Override
    public void publishCancellation(String runId, ChatCancelReason reason) {
        redis.convertAndSend(CANCEL_CHANNEL, runId + ":" + reason.name());
    }

    @Override
    public void publishStreamEvent(ChatStreamEvent event) {
        redis.convertAndSend(STREAM_CHANNEL, event.encode());
    }
}
