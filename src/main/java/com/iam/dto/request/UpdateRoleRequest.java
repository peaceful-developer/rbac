package com.iam.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateRoleRequest(
        @Size(max = 255) String description
) {
}
