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
 * One user's membership in one {@link Project}, and the {@link Role}s they hold
 * within it. This is where project-scoped RBAC actually lives - a user's global
 * account has no roles of its own anymore; every role assignment is relative to a
 * specific project via a row here (see the V3 migration for why this replaced the
 * old single global user_roles table).
 * <p>
 * The {@code SUPER_ADMIN} role is special within this set: only a Master Admin may
 * add or remove it from a membership's roles (see {@code ProjectService}) - a
 * project's own Super Admin cannot mint a rival or remove themselves/others from
 * that role, only manage everyone else.
 */
@Entity
@Table(name = "project_memberships", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "project_membership_roles",
            joinColumns = @JoinColumn(name = "membership_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
