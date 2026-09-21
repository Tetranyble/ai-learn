package com.tetranyble.ailearn.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_runs")
public class ChatRun {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_message_id", nullable = false, updatable = false)
    private ConversationMessage userMessage;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assistant_message_id", nullable = false, updatable = false)
    private ConversationMessage assistantMessage;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20, updatable = false)
    private ChatMode mode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ChatRunStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "cancel_reason", length = 20)
    private ChatCancelReason cancelReason;

    @Column(name = "cancellation_requested_at")
    private Instant cancellationRequestedAt;

    @Column(name = "worker_id", length = 100)
    private String workerId;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ChatRun() {
    }

    public ChatRun(
            String id,
            Conversation conversation,
            ConversationMessage userMessage,
            ConversationMessage assistantMessage,
            ChatMode mode
    ) {
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.conversation = conversation;
        this.userMessage = userMessage;
        this.assistantMessage = assistantMessage;
        this.mode = mode;
        this.status = ChatRunStatus.QUEUED;
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

    public void start(String workerId, Instant leaseExpiresAt) {
        status = ChatRunStatus.PROCESSING;
        this.workerId = workerId;
        this.leaseExpiresAt = leaseExpiresAt;
        startedAt = Instant.now();
    }

    public void renew(String workerId, Instant leaseExpiresAt) {
        if (status == ChatRunStatus.PROCESSING && workerId.equals(this.workerId)) {
            this.leaseExpiresAt = leaseExpiresAt;
        }
    }

    public void requestCancellation(ChatCancelReason reason) {
        if (!status.isTerminal()) {
            cancelReason = reason;
            cancellationRequestedAt = Instant.now();
        }
    }

    public void recoverExpiredLease() {
        if (status == ChatRunStatus.PROCESSING) {
            status = ChatRunStatus.QUEUED;
            workerId = null;
            leaseExpiresAt = null;
            startedAt = null;
        }
    }

    public void complete() {
        finish(ChatRunStatus.COMPLETED, null);
    }

    public void cancel(ChatRunStatus terminalStatus) {
        finish(terminalStatus, null);
    }

    public void fail(String errorCode) {
        finish(ChatRunStatus.FAILED, errorCode);
    }

    private void finish(ChatRunStatus terminalStatus, String errorCode) {
        status = terminalStatus;
        this.errorCode = errorCode;
        workerId = null;
        leaseExpiresAt = null;
        completedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getConversationId() { return conversation.getId(); }
    public ConversationMessage getUserMessage() { return userMessage; }
    public String getUserMessageId() { return userMessage.getId(); }
    public ConversationMessage getAssistantMessage() { return assistantMessage; }
    public String getAssistantMessageId() { return assistantMessage.getId(); }
    public ChatMode getMode() { return mode; }
    public ChatRunStatus getStatus() { return status; }
    public ChatCancelReason getCancelReason() { return cancelReason; }
    public Instant getCancellationRequestedAt() { return cancellationRequestedAt; }
    public String getErrorCode() { return errorCode; }
    public String getWorkerId() { return workerId; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
