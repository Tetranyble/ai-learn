package com.tetranyble.ailearn.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatStreamEventTests {

    @Test
    void safelyRoundTripsRedisPayloadsContainingArbitraryText() {
        ChatStreamEvent event = ChatStreamEvent.update(
                "conversation-id",
                "run-id",
                "message-id",
                "Text with | delimiters, unicode ✓, and\nnewlines",
                ChatRunStatus.INTERRUPTED,
                ChatCancelReason.STEERED,
                null
        ).withSource("node-1");

        assertThat(ChatStreamEvent.decode(event.encode())).isEqualTo(event);
    }
}
