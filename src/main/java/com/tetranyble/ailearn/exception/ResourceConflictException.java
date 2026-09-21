package com.tetranyble.ailearn.exception;

public class ResourceConflictException extends ApiException {

    public ResourceConflictException(String message) {
        super(409, "resource_conflict", message);
    }
}
