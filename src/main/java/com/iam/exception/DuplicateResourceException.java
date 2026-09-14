package com.iam.exception;

/** A unique constraint would be violated (username/email/role name/permission name already exists). Mapped to 409 by GlobalExceptionHandler. */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
