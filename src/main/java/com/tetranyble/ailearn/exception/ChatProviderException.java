package com.tetranyble.ailearn.exception;

public class ChatProviderException extends ApiException {

    public ChatProviderException(Throwable cause) {
        super(
                502,
                "chat_provider_error",
                "The chat service is temporarily unavailable.",
                cause
        );
    }
}
