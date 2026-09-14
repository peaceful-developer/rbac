package com.iam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/projects body - Master Admin only. */
public record CreateProjectRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description
) {
}
