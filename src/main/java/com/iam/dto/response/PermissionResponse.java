package com.iam.dto.response;

import com.iam.domain.Permission;

public record PermissionResponse(Long id, String name, String description) {
    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getName(), permission.getDescription());
    }
}
