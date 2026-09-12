package com.iam.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

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
