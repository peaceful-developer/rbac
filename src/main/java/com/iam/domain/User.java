package com.iam.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * An account in the system. Authentication credentials, profile fields, account-status
 * flags, and the set of {@link Role}s that determine what the account can do all live
 * here - there is deliberately no separate "profile" entity.
 * <p>
 * Wrapped by {@code com.iam.security.UserPrincipal} for Spring Security purposes; see
 * that class for how {@link #enabled} / {@link #accountNonLocked} and the role/permission
 * set become {@code UserDetails} authorities.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt hash - see {@code PasswordEncoder} bean in SecurityConfig. The raw password is never stored. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    /** Admin-controlled kill switch: a disabled account fails authentication even with a correct password. */
    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    /** Admin-controlled lock, independent of {@link #enabled} - e.g. for suspected compromise without deleting the account. */
    @Builder.Default
    @Column(name = "account_non_locked", nullable = false)
    private boolean accountNonLocked = true;

    /**
     * Reserved for a future automatic-lockout-after-N-failed-attempts policy.
     * Currently unused: nothing in this codebase increments or reads it yet, so it
     * stays at 0. Not wired into login failure handling - don't assume it's enforced.
     */
    @Builder.Default
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Eagerly fetched for the same reason as {@code Role.permissions} - see that class. */
    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    /** Stamps both timestamps right before the initial INSERT. */
    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Refreshes {@link #updatedAt} on every UPDATE. */
    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
