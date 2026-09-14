package com.iam.exception;

/** A referenced entity (user/role/permission id or name) doesn't exist. Mapped to 404 by GlobalExceptionHandler. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
