CREATE TABLE chat_outbox_events (
    id CHAR(36) NOT NULL,
    run_id CHAR(36) NOT NULL,
    payload LONGTEXT NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    claimed_by VARCHAR(100) NULL,
    claimed_until TIMESTAMP(6) NULL,
    published_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_chat_outbox_run
        FOREIGN KEY (run_id) REFERENCES chat_runs (id)
        ON DELETE CASCADE,
    INDEX idx_chat_outbox_pending (published_at, claimed_until, created_at),
    INDEX idx_chat_outbox_run_created (run_id, created_at)
);
