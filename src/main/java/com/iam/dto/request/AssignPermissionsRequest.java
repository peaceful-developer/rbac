package com.iam.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/** PUT /api/roles/{id}/permissions body - replaces (not merges with) the role's entire permission set; every name must already exist. */
public record AssignPermissionsRequest(
        @NotEmpty Set<String> permissions
) {
}
