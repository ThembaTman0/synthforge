package com.themba.remitflow;

/**
 * Thrown when a RemittanceOrder status transition is not one of the legal
 * transitions in remitflow-v1-spec.md section 8 rule 5. Mapped to 409 by
 * ApiExceptionHandler.
 */
public class IllegalTransitionException extends RuntimeException {

    public IllegalTransitionException(String message) {
        super(message);
    }
}
