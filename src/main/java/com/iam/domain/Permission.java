package com.iam.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A single grantable action in the system, e.g. {@code USER_WRITE} or {@code ROLE_DELETE}.
 * <p>
 * Permissions are the finest-grained unit of authorization: they are never checked
 * directly against a user, but are grouped into {@link Role}s, which are in turn
 * assigned to {@link User}s. At authentication time, a user's effective permissions
 * are the union of every permission carried by every role they hold (see
 * {@code com.iam.security.UserPrincipal}).
 * <p>
 * The {@code name} is what controllers check via
 * {@code @PreAuthorize("hasAuthority('...')")} and what gets embedded (by name) in
 * issued JWTs - so renaming an existing permission is effectively a breaking change
 * for any code or token that references the old name.
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique, stable identifier used in code and JWTs - e.g. "USER_WRITE". Not a display label. */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** Human-readable explanation of what this permission allows, shown in admin UIs. */
    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Stamps the creation timestamp right before the initial INSERT. */
    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
