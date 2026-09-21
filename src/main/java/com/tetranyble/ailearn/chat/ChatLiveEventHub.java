package com.tetranyble.ailearn.chat;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.UUID;

@Component
public class ChatLiveEventHub {

    private static final Logger log = LoggerFactory.getLogger(ChatLiveEventHub.class);
    private final ChatSignalBus signals;
    private final ChatEventStore eventStore;
    private final String sourceId = UUID.randomUUID().toString();
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<Consumer<ChatStreamEvent>>>
            subscribers = new ConcurrentHashMap<>();

    public ChatLiveEventHub(ChatSignalBus signals, ChatEventStore eventStore) {
        this.signals = signals;
        this.eventStore = eventStore;
    }

    public AutoCloseable subscribe(
            String runId,
            Consumer<ChatStreamEvent> subscriber
    ) {
        subscribers.computeIfAbsent(runId, ignored -> new CopyOnWriteArraySet<>())
                .add(subscriber);
        return () -> unsubscribe(runId, subscriber);
    }

    public void publish(ChatStreamEvent event) {
        ChatStreamEvent sourced = event.withSource(sourceId);
        ChatStreamEvent stored = eventStore.append(sourced);
        deliver(stored);
        signals.publishStreamEvent(stored);
    }

    public void deliverRemote(ChatStreamEvent event) {
        if (!sourceId.equals(event.sourceId())) {
            deliver(event);
        }
    }

    private void deliver(ChatStreamEvent event) {
        var listeners = subscribers.get(event.runId());
        if (listeners != null) {
            listeners.forEach(listener -> {
                try {
                    listener.accept(event);
                } catch (RuntimeException exception) {
                    log.debug("Closing failed SSE subscriber for run {}", event.runId(), exception);
                    unsubscribe(event.runId(), listener);
                }
            });
        }
    }

    private void unsubscribe(
            String runId,
            Consumer<ChatStreamEvent> subscriber
    ) {
        var listeners = subscribers.get(runId);
        if (listeners == null) {
            return;
        }
        listeners.remove(subscriber);
        if (listeners.isEmpty()) {
            subscribers.remove(runId, listeners);
        }
    }
}
