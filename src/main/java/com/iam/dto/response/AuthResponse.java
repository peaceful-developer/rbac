package com.iam.dto.response;

/**
 * Returned by register/login/refresh (see AuthController). {@code accessToken} is a
 * short-lived JWT to send as {@code Authorization: Bearer <accessToken>};
 * {@code refreshToken} is a longer-lived opaque value used only against
 * {@code POST /api/auth/refresh} to obtain a new pair once the access token expires.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresInSeconds) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}
