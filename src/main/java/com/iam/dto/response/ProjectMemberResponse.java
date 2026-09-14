package com.iam.dto.response;

import com.iam.domain.ProjectMembership;
import com.iam.domain.Role;

import java.util.Set;
import java.util.stream.Collectors;

/** One row of a project's member list: who they are, and their role(s) within *this* project specifically. */
public record ProjectMemberResponse(
        Long userId,
        String username,
        String email,
        Set<String> roles
) {
    public static ProjectMemberResponse from(ProjectMembership membership) {
        return new ProjectMemberResponse(
                membership.getUser().getId(),
                membership.getUser().getUsername(),
                membership.getUser().getEmail(),
                membership.getRoles().stream().map(Role::getName).collect(Collectors.toSet())
        );
    }
}
