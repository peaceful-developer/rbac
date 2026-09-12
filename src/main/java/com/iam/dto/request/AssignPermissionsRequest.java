package com.iam.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record AssignPermissionsRequest(
        @NotEmpty Set<String> permissions
) {
}
