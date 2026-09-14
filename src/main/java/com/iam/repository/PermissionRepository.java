package com.iam.repository;

import com.iam.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    /** Permissions are referenced by name throughout (request DTOs use names, not ids) - this is the main lookup. */
    Optional<Permission> findByName(String name);

    /** Uniqueness check used before creating a new permission. */
    boolean existsByName(String name);

    /** Bulk lookup by name; kept for convenience - not currently called elsewhere in this codebase. */
    List<Permission> findByNameIn(List<String> names);
}
