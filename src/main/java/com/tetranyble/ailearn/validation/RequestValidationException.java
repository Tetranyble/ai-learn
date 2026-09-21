package com.tetranyble.ailearn.validation;

import java.util.List;
import java.util.Map;

public class RequestValidationException extends RuntimeException {

    private final Map<String, List<String>> errors;

    public RequestValidationException(Map<String, List<String>> errors) {
        super("The given data was invalid.");
        this.errors = Map.copyOf(errors);
    }

    public static RequestValidationException forField(
            String field,
            String message
    ) {
        return new RequestValidationException(
                Map.of(field, List.of(message))
        );
    }

    public Map<String, List<String>> getErrors() {
        return errors;
    }
}
