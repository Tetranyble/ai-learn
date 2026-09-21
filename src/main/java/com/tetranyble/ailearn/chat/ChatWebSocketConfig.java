package com.tetranyble.ailearn.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class ChatWebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler handler;
    private final String[] allowedOriginPatterns;

    public ChatWebSocketConfig(
            ChatWebSocketHandler handler,
            @Value("${app.chat.websocket.allowed-origin-patterns:*}")
            String[] allowedOriginPatterns
    ) {
        this.handler = handler;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/chat")
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }
}
