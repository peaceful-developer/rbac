package com.iam.dto.response;

import com.iam.domain.User;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Public view of a User entity - notably excludes passwordHash. {@code roles} is the
 * user's *global* role set (just names, not full Role objects) - see ProjectMemberResponse
 * for a user's roles within a specific project, which is a separate, project-scoped set.
 */
public record UserResponse(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        boolean accountNonLocked,
        boolean masterAdmin,
        Set<String> roles,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.isEnabled(),
                user.isAccountNonLocked(),
                user.isMasterAdmin(),
                user.getRoles().stream().map(com.iam.domain.Role::getName).collect(Collectors.toSet()),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
