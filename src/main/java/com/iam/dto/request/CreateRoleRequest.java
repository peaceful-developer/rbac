package com.iam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** POST /api/roles body. {@code permissions} is optional - each name must already exist; null/empty creates a role with no permissions yet. */
public record CreateRoleRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description,
        Set<String> permissions
) {
}
