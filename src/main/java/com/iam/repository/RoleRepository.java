package com.iam.repository;

import com.iam.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    /** Roles are referenced by name throughout (request DTOs use role names, not ids) - this is the main lookup. */
    Optional<Role> findByName(String name);

    /** Uniqueness check used before creating a new role. */
    boolean existsByName(String name);
}
