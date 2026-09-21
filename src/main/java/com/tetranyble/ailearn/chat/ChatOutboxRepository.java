package com.tetranyble.ailearn.chat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Repository
@Transactional(readOnly = true)
public class ChatOutboxRepository {

    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);

    @PersistenceContext
    private EntityManager entityManager;

    private final ApplicationEventPublisher events;

    public ChatOutboxRepository(ApplicationEventPublisher events) {
        this.events = events;
    }

    void append(ChatStreamEvent event) {
        entityManager.persist(new ChatOutboxEvent(event));
        events.publishEvent(new ChatOutboxReadyEvent());
    }

    @Transactional
    public List<ClaimedChatEvent> claimBatch(
            String publisherId,
            int batchSize
    ) {
        Instant now = Instant.now();
        List<ChatOutboxEvent> events = entityManager.createQuery("""
                        select event
                        from ChatOutboxEvent event
                        where event.publishedAt is null
                          and (event.claimedUntil is null or event.claimedUntil < :now)
                        order by event.createdAt asc
                        """, ChatOutboxEvent.class)
                .setParameter("now", now)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(batchSize)
                .getResultList();
        events.forEach(event -> event.claim(
                publisherId,
                now.plus(CLAIM_LEASE)
        ));
        entityManager.flush();
        return events.stream()
                .map(event -> new ClaimedChatEvent(
                        event.getId(),
                        ChatStreamEvent.decode(event.getPayload())
                ))
                .toList();
    }

    @Transactional
    public void markPublished(String eventId, String publisherId) {
        ChatOutboxEvent event = entityManager.find(
                ChatOutboxEvent.class,
                eventId,
                LockModeType.PESSIMISTIC_WRITE
        );
        if (event != null) {
            event.markPublished(publisherId);
        }
    }

    @Transactional
    public void release(String eventId, String publisherId) {
        ChatOutboxEvent event = entityManager.find(
                ChatOutboxEvent.class,
                eventId,
                LockModeType.PESSIMISTIC_WRITE
        );
        if (event != null) {
            event.release(publisherId);
        }
    }

    @Transactional
    public int deletePublishedBefore(Instant cutoff) {
        return entityManager.createQuery("""
                        delete from ChatOutboxEvent event
                        where event.publishedAt < :cutoff
                        """)
                .setParameter("cutoff", cutoff)
                .executeUpdate();
    }
}
