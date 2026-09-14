package com.iam.dto.request;

import jakarta.validation.constraints.NotNull;

/** PATCH /api/users/{id}/master-admin body - Master Admin only, both to grant and revoke. */
public record SetMasterAdminRequest(
        @NotNull Boolean masterAdmin
) {
}
