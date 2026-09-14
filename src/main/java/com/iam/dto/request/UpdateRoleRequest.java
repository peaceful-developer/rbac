package com.iam.dto.request;

import jakarta.validation.constraints.Size;

/** PUT /api/roles/{id} body - description is the only mutable field here; name and permissions have their own dedicated flows. */
public record UpdateRoleRequest(
        @Size(max = 255) String description
) {
}
