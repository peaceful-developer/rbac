package com.iam.security;

import com.iam.domain.ProjectMembership;
import com.iam.repository.ProjectMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Backs {@code @PreAuthorize} checks that depend on *which* project is being acted on
 * - something a flat {@code hasAuthority(...)} string can never express, since holding
 * a permission in one project must not imply holding it in another. Used like:
 * <pre>{@code @PreAuthorize("@projectAuthorizationService.hasProjectAuthority(#projectId, 'PROJECT_MEMBER_WRITE')")}</pre>
 * <p>
 * Unlike the global authority check (which reads from the already-resolved,
 * short-TTL-cached {@code UserPrincipal} sitting in the SecurityContext - see
 * CustomUserDetailsService/CacheConfig), this queries {@code ProjectMembershipRepository}
 * fresh on every call. That's deliberate: project membership changes are far less
 * frequent than the request volume they gate, so the extra query per check is cheap,
 * and it avoids having to invalidate a second, project-shaped cache on every
 * membership/role change.
 */
@Service
@RequiredArgsConstructor
public class ProjectAuthorizationService {

    private final ProjectMembershipRepository projectMembershipRepository;

    /**
     * True if the current user is a Master Admin (who bypasses every per-project
     * check - see UserPrincipal), or holds a project role that carries the given
     * permission name within the specified project.
     */
    public boolean hasProjectAuthority(Long projectId, String authority) {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            return false;
        }
        if (principal.isMasterAdmin()) {
            return true;
        }

        return projectMembershipRepository.findByProjectIdAndUserId(projectId, principal.getId())
                .map(membership -> hasAuthority(membership, authority))
                .orElse(false);
    }

    /** True if the current user is a Master Admin or has any membership at all in the given project. */
    public boolean isProjectMember(Long projectId) {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            return false;
        }
        return principal.isMasterAdmin()
                || projectMembershipRepository.existsByProjectIdAndUserId(projectId, principal.getId());
    }

    private boolean hasAuthority(ProjectMembership membership, String authority) {
        return membership.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> permission.getName().equals(authority));
    }

    private UserPrincipal currentPrincipal() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;
        return principal instanceof UserPrincipal userPrincipal ? userPrincipal : null;
    }
}
