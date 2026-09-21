package com.tetranyble.ailearn.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChatOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(ChatOutboxPublisher.class);

    private final ChatOutboxRepository outbox;
    private final ChatLiveEventHub liveEvents;
    private final int batchSize;
    private final Duration retention;
    private final TaskExecutor executor;
    private final String publisherId = UUID.randomUUID().toString();
    private final AtomicBoolean publishing = new AtomicBoolean();
    private final AtomicBoolean rescanRequested = new AtomicBoolean();

    public ChatOutboxPublisher(
            ChatOutboxRepository outbox,
            ChatLiveEventHub liveEvents,
            @Qualifier("chatTaskExecutor") TaskExecutor executor,
            @Value("${app.chat.outbox.batch-size}") int batchSize,
            @Value("${app.chat.outbox.retention}") Duration retention
    ) {
        this.outbox = outbox;
        this.liveEvents = liveEvents;
        this.executor = executor;
        this.batchSize = batchSize;
        this.retention = retention;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onOutboxReady(ChatOutboxReadyEvent ignored) {
        rescanRequested.set(true);
        executor.execute(this::publishPending);
    }

    @Scheduled(fixedDelayString = "${app.chat.outbox.recovery-interval}")
    void recoverPending() {
        rescanRequested.set(true);
        publishPending();
    }

    private void publishPending() {
        if (!publishing.compareAndSet(false, true)) {
            return;
        }
        try {
            do {
                rescanRequested.set(false);
                var claimedEvents = outbox.claimBatch(publisherId, batchSize);
                for (ClaimedChatEvent claimed : claimedEvents) {
                    try {
                        liveEvents.publish(claimed.event());
                        outbox.markPublished(claimed.id(), publisherId);
                    } catch (RuntimeException exception) {
                        outbox.release(claimed.id(), publisherId);
                        log.warn("Unable to publish chat outbox event {}", claimed.id(), exception);
                    }
                }
                if (claimedEvents.size() == batchSize) {
                    rescanRequested.set(true);
                }
            } while (rescanRequested.get());
        } finally {
            publishing.set(false);
            if (rescanRequested.get()) {
                executor.execute(this::publishPending);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.chat.outbox.cleanup-interval}")
    void cleanupPublished() {
        int deleted = outbox.deletePublishedBefore(Instant.now().minus(retention));
        if (deleted > 0) {
            log.info("Removed {} published chat outbox events", deleted);
        }
    }
}
