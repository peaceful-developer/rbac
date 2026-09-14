package com.iam.exception;

/** A business-rule violation that isn't a Bean Validation failure - e.g. wrong current password on a change-password request. Mapped to 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
