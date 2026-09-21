package com.tetranyble.ailearn.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_outbox_events")
public class ChatOutboxEvent {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "run_id", length = 36, nullable = false, updatable = false)
    private String runId;

    @Column(nullable = false, columnDefinition = "LONGTEXT", updatable = false)
    private String payload;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "claimed_by", length = 100)
    private String claimedBy;

    @Column(name = "claimed_until")
    private Instant claimedUntil;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatOutboxEvent() {
    }

    ChatOutboxEvent(ChatStreamEvent event) {
        this.id = event.eventId();
        this.runId = event.runId();
        this.payload = event.encode();
    }

    @PrePersist
    void beforeInsert() {
        createdAt = Instant.now();
    }

    void claim(String publisherId, Instant until) {
        claimedBy = publisherId;
        claimedUntil = until;
        attempts++;
    }

    void markPublished(String publisherId) {
        if (publisherId.equals(claimedBy)) {
            publishedAt = Instant.now();
            claimedBy = null;
            claimedUntil = null;
        }
    }

    void release(String publisherId) {
        if (publisherId.equals(claimedBy)) {
            claimedBy = null;
            claimedUntil = null;
        }
    }

    String getId() { return id; }
    String getPayload() { return payload; }
}
