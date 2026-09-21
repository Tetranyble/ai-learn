CREATE TABLE chat_conversations (
    id CHAR(36) NOT NULL,
    title VARCHAR(160) NULL,
    next_message_sequence BIGINT NOT NULL DEFAULT 1,
    processing_token CHAR(36) NULL,
    processing_expires_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    last_message_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    INDEX idx_chat_conversations_updated_at (updated_at)
);

CREATE TABLE chat_messages (
    id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    sequence_number BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_chat_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES chat_conversations (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_chat_messages_sequence
        UNIQUE (conversation_id, sequence_number),
    CONSTRAINT uk_chat_messages_idempotency
        UNIQUE (conversation_id, idempotency_key),
    INDEX idx_chat_messages_conversation_created
        (conversation_id, created_at)
);

CREATE TABLE chat_memory (
    conversation_id CHAR(36) NOT NULL,
    messages_json LONGTEXT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,

    PRIMARY KEY (conversation_id),
    CONSTRAINT fk_chat_memory_conversation
        FOREIGN KEY (conversation_id) REFERENCES chat_conversations (id)
        ON DELETE CASCADE
);
