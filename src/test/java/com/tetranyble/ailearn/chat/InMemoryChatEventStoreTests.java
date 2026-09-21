package com.tetranyble.ailearn.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryChatEventStoreTests {

    @Test
    void replaysOnlyEventsAfterTheReconnectCursor() {
        InMemoryChatEventStore store = new InMemoryChatEventStore(10);
        ChatStreamEvent first = store.append(event("one"));
        ChatStreamEvent second = store.append(event("two"));
        ChatStreamEvent third = store.append(event("three"));

        assertThat(store.replay("run-id", first.cursor(), 10))
                .extracting(ChatStreamEvent::eventId)
                .containsExactly(second.eventId(), third.eventId());
    }

    @Test
    void doesNotStoreTheSameOutboxEventTwice() {
        InMemoryChatEventStore store = new InMemoryChatEventStore(10);
        ChatStreamEvent event = event("partial");

        store.append(event);
        store.append(event);

        assertThat(store.replay("run-id", null, 10)).hasSize(1);
    }

    private ChatStreamEvent event(String content) {
        return ChatStreamEvent.update(
                "conversation-id",
                "run-id",
                "message-id",
                content,
                ChatRunStatus.PROCESSING,
                null,
                null
        );
    }
}
