package com.iam.dto.response;

import com.iam.domain.User;

/**
 * A deliberately minimal user projection for "pick someone to add to this project" UIs.
 * <p>
 * Unlike {@link UserResponse}, this carries none of a user's global account state
 * (roles, enabled/locked status, masterAdmin flag) - a project's Super Admin needs to
 * find and select an existing account, not see or reason about its platform-level
 * standing, which is why this is served from a separate, more narrowly-gated endpoint
 * instead of reusing {@code GET /api/users} (which requires {@code USER_READ}, an
 * authority a project Super Admin does not and should not need).
 */
public record CandidateUserResponse(
        Long id,
        String username,
        String email
) {
    public static CandidateUserResponse from(User user) {
        return new CandidateUserResponse(user.getId(), user.getUsername(), user.getEmail());
    }
}
