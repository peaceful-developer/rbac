package com.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code iam.jwt.*} properties from application.yml (see that file for
 * defaults/env-var overrides). Bound automatically at startup via
 * {@code @ConfigurationPropertiesScan} on {@code IamApplication} - no separate
 * {@code @Bean} definition needed.
 *
 * @param secret                   HMAC signing key for access tokens (see JwtService) - must be kept secret and long enough for HS256/HS384.
 * @param accessTokenTtlMinutes    how long an issued access token remains valid.
 * @param refreshTokenTtlDays      how long an issued refresh token remains valid (see AuthService).
 * @param issuer                   embedded as the JWT "iss" claim; informational only, not currently validated on parse.
 */
@ConfigurationProperties(prefix = "iam.jwt")
public record JwtProperties(
        String secret,
        long accessTokenTtlMinutes,
        long refreshTokenTtlDays,
        String issuer
) {
}
