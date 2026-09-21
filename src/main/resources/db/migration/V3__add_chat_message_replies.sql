ALTER TABLE chat_messages
    ADD COLUMN reply_to_id CHAR(36) NULL AFTER idempotency_key,
    ADD CONSTRAINT fk_chat_messages_reply
        FOREIGN KEY (reply_to_id) REFERENCES chat_messages (id)
        ON DELETE SET NULL,
    ADD INDEX idx_chat_messages_reply_to (reply_to_id);
