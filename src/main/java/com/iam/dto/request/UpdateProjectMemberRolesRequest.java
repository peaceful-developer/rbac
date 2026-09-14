package com.iam.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/**
 * PUT /api/projects/{id}/members/{userId}/roles body - replaces (not merges with) a
 * member's role set within this project. Adding or removing {@code SUPER_ADMIN} here
 * requires the caller to be a Master Admin - see ProjectService.
 */
public record UpdateProjectMemberRolesRequest(
        @NotEmpty Set<String> roles
) {
}
