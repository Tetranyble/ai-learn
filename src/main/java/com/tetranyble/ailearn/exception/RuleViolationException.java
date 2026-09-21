package com.tetranyble.ailearn.exception;

public class RuleViolationException extends ApiException {

    public RuleViolationException(String message) {
        super(422, "rule_violation", message);
    }
}
