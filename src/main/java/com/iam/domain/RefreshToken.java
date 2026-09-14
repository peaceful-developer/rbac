package com.iam.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A long-lived, single-use token that lets a client obtain a new short-lived JWT
 * access token without re-entering credentials (see {@code AuthService#refresh}).
 * <p>
 * The raw token value is never stored - only its SHA-256 hash - so a database leak
 * does not by itself expose usable refresh tokens (see
 * {@code com.iam.security.RefreshTokenGenerator}). Refreshing rotates the token:
 * the one used is immediately marked {@link #revoked}, and a new one is issued, so a
 * given raw token value can only ever be exchanged once.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 hash of the raw refresh token; the raw value is never persisted. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    /** True once past {@link #expiresAt}; checked alongside {@link #revoked} before honoring a refresh request. */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
