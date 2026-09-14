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
 * A named, reusable bundle of {@link Permission}s that can be assigned to one or more
 * {@link User}s (many-to-many on both sides: a role can be held by many users, and a
 * user can hold many roles).
 * <p>
 * Seeded by {@code V2__seed_data.sql} with three starting roles - {@code ADMIN} (every
 * permission), {@code MANAGER} (a curated subset), and {@code USER} (none, the default
 * for self-registered accounts) - but roles are otherwise fully dynamic: admins can
 * create new ones and reshape their permission sets at runtime via the {@code /api/roles}
 * endpoints.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique role name, e.g. "ADMIN" - what gets prefixed with "ROLE_" for Spring Security authorities. */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    /**
     * Eagerly fetched: roles are small in number and read far more often than written,
     * and every request that resolves a user's authorities needs this set (see
     * {@code UserPrincipal}), so lazy-loading would just trade one query for another
     * on almost every request anyway.
     */
    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Stamps the creation timestamp right before the initial INSERT. */
    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
