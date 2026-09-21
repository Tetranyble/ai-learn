package com.tetranyble.ailearn.chat;

import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.service.TokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ChatRunCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ChatRunCoordinator.class);
    private static final Duration PARTIAL_FLUSH_INTERVAL = Duration.ofMillis(250);

    private final LearningAssistant assistant;
    private final ChatRunRepository runs;
    private final JpaChatMemoryStore memoryStore;
    private final ChatSignalBus signals;
    private final TaskExecutor executor;
    private final String workerId = UUID.randomUUID().toString();
    private final Set<String> dispatching = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, ActiveGeneration> active = new ConcurrentHashMap<>();

    public ChatRunCoordinator(
            LearningAssistant assistant,
            ChatRunRepository runs,
            JpaChatMemoryStore memoryStore,
            ChatSignalBus signals,
            @Qualifier("chatTaskExecutor") TaskExecutor executor
    ) {
        this.assistant = assistant;
        this.runs = runs;
        this.memoryStore = memoryStore;
        this.signals = signals;
        this.executor = executor;
    }

    public void accepted(EnqueuedChatRun enqueued) {
        if (enqueued.runToCancelId() != null) {
            cancelLocal(enqueued.runToCancelId(), ChatCancelReason.STEERED);
            publishCancellation(enqueued.runToCancelId(), ChatCancelReason.STEERED);
        }

        String conversationId = enqueued.submission().conversationId();
        dispatch(conversationId);
        publishQueued(conversationId);
    }

    public Optional<ChatRunResponse> cancelActive(String conversationId) {
        Optional<ChatRunResponse> cancelled = runs.requestActiveCancellation(
                conversationId,
                ChatCancelReason.USER
        );
        cancelled.ifPresent(run -> {
            cancelLocal(run.id(), ChatCancelReason.USER);
            publishCancellation(run.id(), ChatCancelReason.USER);
        });
        return cancelled;
    }

    public Optional<ChatRunResponse> cancelRun(
            String conversationId,
            String runId
    ) {
        Optional<ChatRunResponse> cancelled = runs.requestCancellation(
                conversationId,
                runId,
                ChatCancelReason.USER
        );
        cancelled.ifPresent(run -> {
            cancelLocal(run.id(), ChatCancelReason.USER);
            publishCancellation(run.id(), ChatCancelReason.USER);
        });
        return cancelled;
    }

    public void dispatch(String conversationId) {
        if (!dispatching.add(conversationId)) {
            return;
        }

        executor.execute(() -> {
            try {
                runs.claimNext(conversationId, workerId)
                        .ifPresent(this::start);
            } catch (RuntimeException exception) {
                log.error("Unable to dispatch chat conversation {}", conversationId, exception);
            } finally {
                dispatching.remove(conversationId);
            }
        });
    }

    public void cancelLocal(String runId, ChatCancelReason reason) {
        ActiveGeneration generation = active.get(runId);
        if (generation == null) {
            return;
        }

        generation.cancelReason.compareAndSet(null, reason);
        StreamingHandle handle = generation.handle.get();
        if (handle != null) {
            finishCancelled(generation, handle);
        }
    }

    @Scheduled(fixedDelayString = "${app.chat.recovery-scan-interval}")
    void dispatchWaitingRuns() {
        runs.findDispatchableConversationIds(100).forEach(this::dispatch);
    }

    private void start(ChatRunWork work) {
        resetMemory(work.conversationId());
        ActiveGeneration generation = new ActiveGeneration(work);
        active.put(work.runId(), generation);

        try {
            TokenStream stream = assistant.chat(work.conversationId(), work.userMessage());
            stream.onPartialResponseWithContext((partial, context) ->
                            onPartial(generation, partial.text(), context))
                    .onCompleteResponse(response -> {
                        String text = response.aiMessage().text();
                        finishCompleted(generation, text == null
                                ? generation.content.toString()
                                : text);
                    })
                    .onError(error -> finishFailed(generation, error))
                    .start();
        } catch (RuntimeException exception) {
            finishFailed(generation, exception);
        }
    }

    private void onPartial(
            ActiveGeneration generation,
            String partial,
            PartialResponseContext context
    ) {
        if (generation.terminal.get()) {
            return;
        }

        generation.handle.compareAndSet(null, context.streamingHandle());
        generation.content.append(partial);

        Instant now = Instant.now();
        if (Duration.between(generation.lastFlush, now)
                .compareTo(PARTIAL_FLUSH_INTERVAL) >= 0) {
            generation.lastFlush = now;
            runs.updatePartial(
                    generation.work.runId(),
                    workerId,
                    generation.content.toString()
            );

            runs.cancellationReason(generation.work.runId())
                    .ifPresent(reason -> generation.cancelReason.compareAndSet(null, reason));
        }

        if (generation.cancelReason.get() != null) {
            finishCancelled(generation, context.streamingHandle());
        }
    }

    private void finishCompleted(ActiveGeneration generation, String content) {
        if (!generation.terminal.compareAndSet(false, true)) {
            return;
        }

        runs.complete(generation.work.runId(), workerId, content);
        generation.content.replace(0, generation.content.length(), content);
        cleanup(generation, false);
    }

    private void finishCancelled(
            ActiveGeneration generation,
            StreamingHandle handle
    ) {
        if (!generation.terminal.compareAndSet(false, true)) {
            return;
        }

        handle.cancel();
        ChatCancelReason reason = generation.cancelReason.get();
        if (reason == null) {
            reason = ChatCancelReason.USER;
        }
        runs.cancel(
                generation.work.runId(),
                workerId,
                generation.content.toString(),
                reason
        );
        cleanup(generation, true);
    }

    private void finishFailed(ActiveGeneration generation, Throwable error) {
        if (!generation.terminal.compareAndSet(false, true)) {
            return;
        }

        Optional<ChatCancelReason> cancellation = Optional.ofNullable(
                generation.cancelReason.get()
        ).or(() -> runs.cancellationReason(generation.work.runId()));

        if (cancellation.isPresent()) {
            runs.cancel(
                    generation.work.runId(),
                    workerId,
                    generation.content.toString(),
                    cancellation.get()
            );
        } else {
            log.error("Chat provider failed for run {}", generation.work.runId(), error);
            runs.fail(
                    generation.work.runId(),
                    workerId,
                    generation.content.toString()
            );
        }
        cleanup(generation, true);
    }

    private void cleanup(ActiveGeneration generation, boolean discardMemory) {
        active.remove(generation.work.runId(), generation);
        if (discardMemory) {
            resetMemory(generation.work.conversationId());
        } else {
            assistant.evictChatMemory(generation.work.conversationId());
        }
        dispatch(generation.work.conversationId());
        publishQueued(generation.work.conversationId());
    }

    private void resetMemory(String conversationId) {
        memoryStore.deleteMessages(conversationId);
        assistant.evictChatMemory(conversationId);
    }

    private void publishQueued(String conversationId) {
        try {
            signals.publishQueued(conversationId);
        } catch (RuntimeException exception) {
            log.warn(
                    "Unable to publish queued chat signal for conversation {}",
                    conversationId,
                    exception
            );
        }
    }

    private void publishCancellation(String runId, ChatCancelReason reason) {
        try {
            signals.publishCancellation(runId, reason);
        } catch (RuntimeException exception) {
            log.warn(
                    "Unable to publish chat cancellation signal for run {}",
                    runId,
                    exception
            );
        }
    }

    private static final class ActiveGeneration {

        private final ChatRunWork work;
        private final StringBuilder content = new StringBuilder();
        private final AtomicReference<StreamingHandle> handle = new AtomicReference<>();
        private final AtomicReference<ChatCancelReason> cancelReason = new AtomicReference<>();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private volatile Instant lastFlush = Instant.EPOCH;

        private ActiveGeneration(ChatRunWork work) {
            this.work = work;
        }
    }
}
