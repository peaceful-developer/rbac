package com.iam.exception;

/** A refresh token is unknown, expired, already revoked/used, or its owning account can no longer authenticate. Mapped to 401 by GlobalExceptionHandler. */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
