package com.tetranyble.ailearn.chat;

public enum ChatRunStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    INTERRUPTED,
    CANCELLED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED
                || this == INTERRUPTED
                || this == CANCELLED
                || this == FAILED;
    }
}
