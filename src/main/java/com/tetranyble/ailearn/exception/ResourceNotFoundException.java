package com.tetranyble.ailearn.exception;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(404, "resource_not_found", resource + " not found: " + identifier);
    }
}
