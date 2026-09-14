package com.iam.dto.request;

import jakarta.validation.constraints.Size;

/** PUT /api/projects/{id} body - Master Admin only. Every field optional/applied-if-present, like UpdateUserRequest. */
public record UpdateProjectRequest(
        @Size(max = 150) String name,
        @Size(max = 500) String description
) {
}
