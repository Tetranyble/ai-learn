package com.tetranyble.ailearn.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.chat.redis.enabled", havingValue = "true")
public class RedisChatEventStore implements ChatEventStore {

    private static final String KEY_PREFIX = "ai-learn:chat:run:";
    private static final String KEY_SUFFIX = ":events";
    private static final String PAYLOAD = "payload";

    private final StringRedisTemplate redis;
    private final Duration retention;
    private final int maxPerRun;

    public RedisChatEventStore(
            StringRedisTemplate redis,
            @Value("${app.chat.events.retention}") Duration retention,
            @Value("${app.chat.events.max-per-run}") int maxPerRun
    ) {
        this.redis = redis;
        this.retention = retention;
        this.maxPerRun = maxPerRun;
    }

    @Override
    public ChatStreamEvent append(ChatStreamEvent event) {
        String key = key(event.runId());
        RecordId recordId = redis.opsForStream().add(key, Map.of(PAYLOAD, event.encode()));
        if (recordId == null) {
            throw new IllegalStateException("Redis did not return a chat event cursor");
        }
        redis.opsForStream().trim(key, maxPerRun, true);
        redis.expire(key, retention);
        return event.withCursor(recordId.getValue());
    }

    @Override
    public List<ChatStreamEvent> replay(String runId, String afterCursor, int limit) {
        Range<String> range = afterCursor == null || afterCursor.isBlank()
                ? Range.unbounded()
                : Range.rightUnbounded(Range.Bound.exclusive(afterCursor));
        List<MapRecord<String, Object, Object>> records = redis.opsForStream()
                .range(key(runId), range, Limit.limit().count(limit));
        if (records == null) {
            return List.of();
        }
        return records.stream()
                .map(record -> ChatStreamEvent.decode(
                        String.valueOf(record.getValue().get(PAYLOAD))
                ).withCursor(record.getId().getValue()))
                .toList();
    }

    private String key(String runId) {
        return KEY_PREFIX + runId + KEY_SUFFIX;
    }
}
