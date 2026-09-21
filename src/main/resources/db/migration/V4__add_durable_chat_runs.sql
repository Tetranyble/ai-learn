ALTER TABLE chat_messages
    MODIFY COLUMN content LONGTEXT NOT NULL,
    ADD COLUMN run_id CHAR(36) NULL AFTER reply_to_id,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' AFTER role,
    ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) AFTER created_at,
    ADD INDEX idx_chat_messages_run (run_id),
    ADD INDEX idx_chat_messages_conversation_status (conversation_id, status);

CREATE TABLE chat_runs (
    id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    user_message_id CHAR(36) NOT NULL,
    assistant_message_id CHAR(36) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    cancel_reason VARCHAR(20) NULL,
    cancellation_requested_at TIMESTAMP(6) NULL,
    worker_id VARCHAR(100) NULL,
    lease_expires_at TIMESTAMP(6) NULL,
    error_code VARCHAR(100) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT fk_chat_runs_conversation
        FOREIGN KEY (conversation_id) REFERENCES chat_conversations (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_chat_runs_user_message
        FOREIGN KEY (user_message_id) REFERENCES chat_messages (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_chat_runs_assistant_message
        FOREIGN KEY (assistant_message_id) REFERENCES chat_messages (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_chat_runs_user_message UNIQUE (user_message_id),
    CONSTRAINT uk_chat_runs_assistant_message UNIQUE (assistant_message_id),
    INDEX idx_chat_runs_dispatch (status, lease_expires_at, created_at),
    INDEX idx_chat_runs_conversation_created (conversation_id, created_at)
);
