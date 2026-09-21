package com.tetranyble.ailearn.exception;

public class ResourceGoneException extends ApiException {

    public ResourceGoneException(String message) {
        super(410, "resource_gone", message);
    }
}
