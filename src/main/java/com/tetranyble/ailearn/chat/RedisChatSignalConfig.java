package com.tetranyble.ailearn.chat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(name = "app.chat.redis.enabled", havingValue = "true")
public class RedisChatSignalConfig {

    @Bean
    RedisMessageListenerContainer chatRedisListenerContainer(
            RedisConnectionFactory connectionFactory,
            ChatRunCoordinator coordinator,
            ChatLiveEventHub liveEvents
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, pattern) -> coordinator.dispatch(
                        new String(message.getBody(), StandardCharsets.UTF_8)
                ),
                new ChannelTopic(RedisChatSignalBus.QUEUED_CHANNEL)
        );
        container.addMessageListener(
                (message, pattern) -> {
                    String payload = new String(message.getBody(), StandardCharsets.UTF_8);
                    int separator = payload.lastIndexOf(':');
                    if (separator > 0) {
                        coordinator.cancelLocal(
                                payload.substring(0, separator),
                                ChatCancelReason.valueOf(payload.substring(separator + 1))
                        );
                    }
                },
                new ChannelTopic(RedisChatSignalBus.CANCEL_CHANNEL)
        );
        container.addMessageListener(
                (message, pattern) -> liveEvents.deliverRemote(
                        ChatStreamEvent.decode(
                                new String(message.getBody(), StandardCharsets.UTF_8)
                        )
                ),
                new ChannelTopic(RedisChatSignalBus.STREAM_CHANNEL)
        );
        return container;
    }
}
