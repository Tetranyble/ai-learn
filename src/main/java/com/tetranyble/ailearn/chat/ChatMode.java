package com.tetranyble.ailearn.chat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ChatMode {
    QUEUE,
    INTERRUPT;

    @JsonCreator
    public static ChatMode from(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase());
    }

    @JsonValue
    public String value() {
        return name().toLowerCase();
    }
}
