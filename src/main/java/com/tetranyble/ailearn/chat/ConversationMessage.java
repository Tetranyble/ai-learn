package com.tetranyble.ailearn.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
public class ConversationMessage {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private long sequence;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20, updatable = false)
    private MessageRole role;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id", updatable = false)
    private ConversationMessage replyTo;

    @Column(name = "run_id", length = 36, updatable = false)
    private String runId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private MessageStatus status;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationMessage() {
    }

    public ConversationMessage(
            Conversation conversation,
            long sequence,
            MessageRole role,
            String content,
            String idempotencyKey,
            ConversationMessage replyTo
    ) {
        this(conversation, sequence, role, content, idempotencyKey, replyTo, null, MessageStatus.COMPLETED);
    }

    public ConversationMessage(
            Conversation conversation,
            long sequence,
            MessageRole role,
            String content,
            String idempotencyKey,
            ConversationMessage replyTo,
            String runId,
            MessageStatus status
    ) {
        this.id = UUID.randomUUID().toString();
        this.conversation = conversation;
        this.sequence = sequence;
        this.role = role;
        this.content = content;
        this.idempotencyKey = idempotencyKey;
        this.replyTo = replyTo;
        this.runId = runId;
        this.status = status;
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

    public void replaceContent(String content) {
        this.content = content;
    }

    public void transitionTo(MessageStatus status) {
        this.status = status;
    }

    public String getId() { return id; }
    public String getConversationId() { return conversation.getId(); }
    public long getSequence() { return sequence; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public String getReplyToId() { return replyTo == null ? null : replyTo.getId(); }
    public String getRunId() { return runId; }
    public MessageStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
