package com.iam.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A tenant in the system (see the README's SaaS framing: each Project represents one
 * onboarded customer/organization). Created and managed only by a Master Admin
 * ({@code ProjectController}); a project's day-to-day membership is then delegated to
 * whichever user(s) a Master Admin assigns the {@code SUPER_ADMIN} role to within it
 * - see {@link ProjectMembership}.
 * <p>
 * Deliberately has no direct collection of members/roles here - that lives in
 * {@link ProjectMembership} instead, queried via {@code ProjectMembershipRepository},
 * so listing a project never has to eagerly pull its entire membership.
 */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
