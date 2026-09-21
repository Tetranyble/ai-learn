package com.tetranyble.ailearn.chat;

import com.tetranyble.ailearn.exception.ResourceNotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ChatRunSseService {

    private final ChatRunRepository runs;
    private final ChatLiveEventHub liveEvents;
    private final ChatEventStore eventStore;
    private final TaskScheduler scheduler;
    private final Duration heartbeatInterval;

    public ChatRunSseService(
            ChatRunRepository runs,
            ChatLiveEventHub liveEvents,
            ChatEventStore eventStore,
            @Qualifier("chatStreamScheduler") TaskScheduler scheduler,
            @Value("${app.chat.sse.heartbeat-interval}") long heartbeatInterval
    ) {
        this.runs = runs;
        this.liveEvents = liveEvents;
        this.eventStore = eventStore;
        this.scheduler = scheduler;
        this.heartbeatInterval = Duration.ofMillis(heartbeatInterval);
    }

    public void stream(
            String conversationId,
            String runId,
            String lastEventId,
            SseEventSink sink,
            Sse sse
    ) {
        requireState(conversationId, runId);

        Subscription subscription = new Subscription(
                conversationId,
                runId,
                sink,
                sse
        );
        subscription.registration = liveEvents.subscribe(runId, subscription::receive);

        // A second read closes the small race between the first snapshot and subscription.
        subscription.initialize(
                eventStore.replay(runId, lastEventId, 500),
                requireState(conversationId, runId)
        );
        if (!subscription.closed.get()) {
            subscription.heartbeat = scheduler.scheduleWithFixedDelay(
                    subscription::heartbeat,
                    heartbeatInterval
            );
        }
    }

    public void stream(
            String conversationId,
            String runId,
            SseEventSink sink,
            Sse sse
    ) {
        stream(conversationId, runId, null, sink, sse);
    }

    private ChatStreamEvent requireState(String conversationId, String runId) {
        return runs.findStreamState(conversationId, runId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatRun", runId));
    }

    private final class Subscription {

        private final String conversationId;
        private final String runId;
        private final SseEventSink sink;
        private final Sse sse;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicLong sequence = new AtomicLong();
        private final Set<String> deliveredEventIds = new HashSet<>();
        private ChatStreamEvent pending;
        private ChatStreamEvent last;
        private boolean initialized;
        private AutoCloseable registration;
        private ScheduledFuture<?> heartbeat;

        private Subscription(
                String conversationId,
                String runId,
                SseEventSink sink,
                Sse sse
        ) {
            this.conversationId = conversationId;
            this.runId = runId;
            this.sink = sink;
            this.sse = sse;
        }

        synchronized void initialize(
                List<ChatStreamEvent> replay,
                ChatStreamEvent snapshot
        ) {
            initialized = true;
            replay.forEach(this::emit);
            if (!closed.get() && !sameState(last, snapshot)) {
                emit(snapshot);
            }
            if (!closed.get() && pending != null && !sameState(last, pending)) {
                emit(pending);
            }
            pending = null;
        }

        synchronized void receive(ChatStreamEvent event) {
            if (!initialized) {
                pending = event;
                return;
            }
            emit(event);
        }

        synchronized void heartbeat() {
            if (closed.get() || sink.isClosed()) {
                close();
                return;
            }

            var current = runs.findStreamState(conversationId, runId);
            if (current.isEmpty()) {
                close();
                return;
            }
            if (!sameState(last, current.get())) {
                emit(current.get());
                return;
            }

            OutboundSseEvent heartbeatEvent = sse.newEventBuilder()
                    .name("heartbeat")
                    .id(runId + ":" + sequence.incrementAndGet())
                    .mediaType(MediaType.TEXT_PLAIN_TYPE)
                    .data(String.class, Instant.now().toString())
                    .build();
            sink.send(heartbeatEvent).whenComplete((ignored, error) -> {
                if (error != null) {
                    close();
                }
            });
        }

        private void emit(ChatStreamEvent event) {
            if (closed.get() || sink.isClosed()) {
                close();
                return;
            }

            if (event.eventId() != null && !deliveredEventIds.add(event.eventId())) {
                return;
            }
            last = event;
            String outboundId = event.cursor() == null
                    ? event.eventId()
                    : event.cursor();
            OutboundSseEvent outbound = sse.newEventBuilder()
                    .name("chat")
                    .id(outboundId)
                    .reconnectDelay(1_000)
                    .mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .data(ChatStreamEvent.class, event.withSource(null))
                    .build();

            try {
                sink.send(outbound).whenComplete((ignored, error) -> {
                    if (error != null || event.terminal()) {
                        close();
                    }
                });
            } catch (RuntimeException exception) {
                close();
            }
        }

        private boolean sameState(ChatStreamEvent first, ChatStreamEvent second) {
            return first != null
                    && first.status().equals(second.status())
                    && java.util.Objects.equals(first.content(), second.content())
                    && java.util.Objects.equals(first.cancelReason(), second.cancelReason())
                    && java.util.Objects.equals(first.errorCode(), second.errorCode());
        }

        private void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
            if (registration != null) {
                try {
                    registration.close();
                } catch (Exception ignored) {
                    // Registration cleanup is best effort during disconnect.
                }
            }
            if (!sink.isClosed()) {
                try {
                    sink.close();
                } catch (java.io.IOException ignored) {
                    // The browser may already have disconnected.
                }
            }
        }
    }
}
