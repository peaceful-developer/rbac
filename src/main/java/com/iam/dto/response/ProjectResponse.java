package com.iam.dto.response;

import com.iam.domain.Project;

import java.time.Instant;
import java.util.Set;

/**
 * Public view of a Project. {@code myRoles} is the *caller's own* roles within this
 * project (empty if they have no membership, e.g. a Master Admin just browsing, or a
 * user in some other project) - see ProjectService#listProjects, which is the only
 * place this gets populated relative to who's asking. It's what the frontend uses to
 * decide whether to show project-management actions, since a global JWT authority
 * can't express "Super Admin of project 42 specifically" (see ProjectAuthorizationService).
 */
public record ProjectResponse(
        Long id,
        String name,
        String description,
        Set<String> myRoles,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(Project project, Set<String> myRoles) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                myRoles,
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
