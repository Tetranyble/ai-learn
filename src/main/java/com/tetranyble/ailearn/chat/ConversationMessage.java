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

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
        this.id = UUID.randomUUID().toString();
        this.conversation = conversation;
        this.sequence = sequence;
        this.role = role;
        this.content = content;
        this.idempotencyKey = idempotencyKey;
        this.replyTo = replyTo;
    }

    @PrePersist
    void beforeInsert() {
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getConversationId() { return conversation.getId(); }
    public long getSequence() { return sequence; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public String getReplyToId() { return replyTo == null ? null : replyTo.getId(); }
    public Instant getCreatedAt() { return createdAt; }
}
