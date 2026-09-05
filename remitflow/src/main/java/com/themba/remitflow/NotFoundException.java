package com.themba.remitflow;

/** Thrown when a referenced entity id does not exist. Mapped to 404 by ApiExceptionHandler. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
