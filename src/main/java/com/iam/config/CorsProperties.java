package com.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds {@code iam.cors.allowed-origins} (comma-separated in application.yml / the
 * {@code CORS_ALLOWED_ORIGINS} env var) - the browser origins allowed to call this API
 * cross-origin. Used by {@code SecurityConfig}'s CORS configuration.
 */
@ConfigurationProperties(prefix = "iam.cors")
public record CorsProperties(List<String> allowedOrigins) {

    /** Overrides the generated record accessor so an unset/empty property still yields a sane local-dev default instead of null/empty. */
    public List<String> allowedOrigins() {
        return allowedOrigins == null || allowedOrigins.isEmpty() ? List.of("http://localhost:3000") : allowedOrigins;
    }
}
