package com.iam.repository;

import com.iam.domain.ProjectMembership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, Long> {

    /** The core lookup behind every per-project permission check - see ProjectAuthorizationService. */
    @EntityGraph(attributePaths = {"roles", "roles.permissions", "user"})
    Optional<ProjectMembership> findByProjectIdAndUserId(Long projectId, Long userId);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    /** A project's full member list, with each member's roles - see ProjectService#listMembers. */
    @EntityGraph(attributePaths = {"roles", "roles.permissions", "user"})
    List<ProjectMembership> findByProjectId(Long projectId);

    /** Every project a given user belongs to, with their roles in each - used to compute "my projects" for non-Master-Admins. */
    @EntityGraph(attributePaths = {"roles", "roles.permissions", "project"})
    List<ProjectMembership> findByUserId(Long userId);
}
