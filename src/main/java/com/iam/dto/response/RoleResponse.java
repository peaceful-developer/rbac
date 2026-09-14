package com.iam.dto.response;

import com.iam.domain.Role;

import java.util.Set;
import java.util.stream.Collectors;

/** Public view of a Role entity, with its permissions expanded to full objects (not just names) so clients don't need a second lookup. */
public record RoleResponse(Long id, String name, String description, Set<PermissionResponse> permissions) {
    public static RoleResponse from(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getPermissions().stream().map(PermissionResponse::from).collect(Collectors.toSet())
        );
    }
}
