package com.iam.repository;

import com.iam.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Loads a user with roles and role-permissions eagerly fetched in one query
     * (via the entity graph) instead of relying on lazy-loading N+1 queries later.
     * This is the lookup used on every authenticated request, via
     * {@code CustomUserDetailsService} - see that class for why it's cached.
     */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithRolesByUsername(String username);

    /** Same as {@link #findWithRolesByUsername}, keyed by id - used after a refresh-token exchange and by admin lookups. */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithRolesById(Long id);

    /** Used for pre-insert uniqueness checks at registration/user-creation time. */
    boolean existsByUsername(String username);

    /** Used for pre-insert uniqueness checks at registration/user-creation time. */
    boolean existsByEmail(String email);

    /** Plain (non-role-fetching) lookups, kept for convenience - not currently called elsewhere in this codebase. */
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);
}
