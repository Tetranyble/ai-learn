package com.tetranyble.ailearn.exception;

public class ConversationBusyException extends ApiException {

    public ConversationBusyException() {
        super(409, "conversation_busy", "Another message is already being processed.");
    }
}
