package com.iam.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/** PUT /api/users/{id}/roles body - replaces (not merges with) the user's entire role set; every name must already exist. */
public record AssignRolesRequest(
        @NotEmpty Set<String> roles
) {
}
