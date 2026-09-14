package com.iam.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * POST /api/projects/{id}/users body - creates a brand-new account <em>and</em> its
 * membership of this project in one call.
 * <p>
 * Distinct from {@link CreateUserRequest} (POST /api/users, {@code USER_WRITE}-gated
 * platform administration): {@code roles} here are the roles the new user gets
 * <em>within this project</em>, not global {@code User.roles}. The account itself is
 * created with the same permission-less baseline {@code USER} role that
 * self-registration grants, so everything it can actually do comes from the project
 * membership - see ProjectService#createUserInProject.
 */
public record CreateProjectUserRequest(
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$")
        String username,

        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank @Size(min = 8, max = 100)
        String password,

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        /** Project role names for the new member; each must already exist in the global role catalog. */
        @NotEmpty
        Set<String> roles
) {
}
