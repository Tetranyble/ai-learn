package com.tetranyble.ailearn.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_conversations")
public class Conversation {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(length = 160)
    private String title;

    @Column(name = "next_message_sequence", nullable = false)
    private long nextMessageSequence = 1;

    @Column(name = "processing_token", length = 36)
    private String processingToken;

    @Column(name = "processing_expires_at")
    private Instant processingExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Conversation() {
    }

    public Conversation(String title) {
        this.id = UUID.randomUUID().toString();
        this.title = normalizeTitle(title);
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isProcessingAt(Instant now) {
        return processingToken != null
                && processingExpiresAt != null
                && processingExpiresAt.isAfter(now);
    }

    public void acquire(String token, Instant expiresAt) {
        processingToken = token;
        processingExpiresAt = expiresAt;
    }

    public void release(String token) {
        if (token.equals(processingToken)) {
            processingToken = null;
            processingExpiresAt = null;
        }
    }

    public long takeNextSequence() {
        return nextMessageSequence++;
    }

    public void recordMessageAt(Instant time, String firstMessage) {
        lastMessageAt = time;

        if (title == null) {
            title = normalizeTitle(firstMessage);
        }
    }

    public void rename(String title) {
        this.title = normalizeTitle(title);
    }

    private String normalizeTitle(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.substring(0, Math.min(normalized.length(), 160));
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastMessageAt() { return lastMessageAt; }
}
