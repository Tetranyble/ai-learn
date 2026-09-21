package com.tetranyble.ailearn.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_memory")
public class ChatMemorySnapshot {

    @Id
    @Column(name = "conversation_id", length = 36, nullable = false)
    private String conversationId;

    @Column(name = "messages_json", nullable = false, columnDefinition = "LONGTEXT")
    private String messagesJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChatMemorySnapshot() {
    }

    public ChatMemorySnapshot(String conversationId, String messagesJson) {
        this.conversationId = conversationId;
        this.messagesJson = messagesJson;
    }

    public void replace(String messagesJson) {
        this.messagesJson = messagesJson;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public String getMessagesJson() { 
        return messagesJson; 
    }
}
