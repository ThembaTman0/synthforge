package com.themba.remitflow;

/**
 * Thrown when a request is well-formed but violates a domain rule from
 * remitflow-v1-spec.md section 8 (e.g. amount exceeds
 * remitflow.max-order-amount). Mapped to 422 by ApiExceptionHandler.
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
