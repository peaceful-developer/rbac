package com.iam.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * POST /api/projects/{id}/members body - adds an existing user (by id; users are
 * created/registered separately, see UserController/AuthController) to the project
 * with the given role(s). Including {@code SUPER_ADMIN} in {@code roles} requires the
 * caller to be a Master Admin - see ProjectService.
 */
public record AddProjectMemberRequest(
        @NotNull Long userId,
        @NotEmpty Set<String> roles
) {
}
