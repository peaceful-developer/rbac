package com.iam.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/users/{id} body. Every field is optional (boxed {@code Boolean}, not
 * {@code boolean}) and only applied if non-null - see UserService#updateUser - so a
 * caller can flip just {@code enabled} without resending the whole profile. Does not
 * cover roles (see AssignRolesRequest) or password (self-service only, see
 * ChangePasswordRequest).
 */
public record UpdateUserRequest(
        @Email @Size(max = 255)
        String email,

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        Boolean enabled,

        Boolean accountNonLocked
) {
}
