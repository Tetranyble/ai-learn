package com.tetranyble.ailearn.chat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(
        name = "app.chat.redis.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class InMemoryChatEventStore implements ChatEventStore {

    private final ConcurrentHashMap<String, ArrayDeque<ChatStreamEvent>> streams =
            new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final int maxPerRun;

    public InMemoryChatEventStore(
            @Value("${app.chat.events.max-per-run}") int maxPerRun
    ) {
        this.maxPerRun = maxPerRun;
    }

    @Override
    public ChatStreamEvent append(ChatStreamEvent event) {
        ChatStreamEvent stored = event.withCursor(Long.toString(sequence.incrementAndGet()));
        ArrayDeque<ChatStreamEvent> stream = streams.computeIfAbsent(
                event.runId(),
                ignored -> new ArrayDeque<>()
        );
        synchronized (stream) {
            if (stream.stream().noneMatch(existing -> existing.eventId().equals(event.eventId()))) {
                stream.addLast(stored);
                while (stream.size() > maxPerRun) {
                    stream.removeFirst();
                }
            }
        }
        return stored;
    }

    @Override
    public List<ChatStreamEvent> replay(String runId, String afterCursor, int limit) {
        ArrayDeque<ChatStreamEvent> stream = streams.get(runId);
        if (stream == null) {
            return List.of();
        }
        long after = parseCursor(afterCursor);
        synchronized (stream) {
            List<ChatStreamEvent> result = new ArrayList<>();
            for (ChatStreamEvent event : stream) {
                if (parseCursor(event.cursor()) > after) {
                    result.add(event);
                    if (result.size() == limit) {
                        break;
                    }
                }
            }
            return result;
        }
    }

    private long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
