package com.iam.service;

import com.iam.domain.Project;
import com.iam.domain.ProjectMembership;
import com.iam.domain.Role;
import com.iam.domain.User;
import com.iam.dto.request.AddProjectMemberRequest;
import com.iam.dto.request.CreateProjectRequest;
import com.iam.dto.request.UpdateProjectMemberRolesRequest;
import com.iam.dto.request.UpdateProjectRequest;
import com.iam.dto.response.CandidateUserResponse;
import com.iam.dto.response.ProjectMemberResponse;
import com.iam.dto.response.ProjectResponse;
import com.iam.exception.DuplicateResourceException;
import com.iam.exception.ResourceNotFoundException;
import com.iam.repository.ProjectMembershipRepository;
import com.iam.repository.ProjectRepository;
import com.iam.repository.RoleRepository;
import com.iam.repository.UserRepository;
import com.iam.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Projects (tenants) and their membership. Project CRUD is Master-Admin-only (enforced
 * at the controller via plain {@code hasAuthority('MASTER_ADMIN')}); membership
 * management is project-scoped (enforced at the controller via
 * {@code ProjectAuthorizationService}, which lets a Master Admin or that specific
 * project's Super Admin through). What this class additionally enforces, which
 * neither of those checks can express: only a Master Admin may ever add, promote to,
 * or remove the {@code SUPER_ADMIN} role from a membership - see
 * {@link #requireMasterAdminForSuperAdminChanges}.
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository projectMembershipRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    /** Master Admins see every project; everyone else sees only projects they're a member of. Either way, {@code myRoles} reflects the caller's own membership, if any. */
    @Transactional(readOnly = true)
    public List<ProjectResponse> listProjects(UserPrincipal caller) {
        if (caller.isMasterAdmin()) {
            var myMembershipsByProjectId = projectMembershipRepository.findByUserId(caller.getId()).stream()
                    .collect(Collectors.toMap(m -> m.getProject().getId(), this::roleNames));
            return projectRepository.findAll().stream()
                    .map(p -> ProjectResponse.from(p, myMembershipsByProjectId.getOrDefault(p.getId(), Set.of())))
                    .toList();
        }

        return projectMembershipRepository.findByUserId(caller.getId()).stream()
                .map(m -> ProjectResponse.from(m.getProject(), roleNames(m)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(Long projectId, UserPrincipal caller) {
        Project project = findProject(projectId);
        Set<String> myRoles = projectMembershipRepository.findByProjectIdAndUserId(projectId, caller.getId())
                .map(this::roleNames)
                .orElse(Set.of());
        return ProjectResponse.from(project, myRoles);
    }

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        if (projectRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Project '" + request.name() + "' already exists");
        }
        Project project = Project.builder()
                .name(request.name())
                .description(request.description())
                .build();
        return ProjectResponse.from(projectRepository.save(project), Set.of());
    }

    @Transactional
    public ProjectResponse updateProject(Long projectId, UpdateProjectRequest request) {
        Project project = findProject(projectId);
        if (request.name() != null && !request.name().equalsIgnoreCase(project.getName())) {
            if (projectRepository.existsByName(request.name())) {
                throw new DuplicateResourceException("Project '" + request.name() + "' already exists");
            }
            project.setName(request.name());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        return ProjectResponse.from(projectRepository.save(project), Set.of());
    }

    /** Membership rows cascade-delete at the database level (see the V3 migration) - no explicit cleanup needed here. */
    @Transactional
    public void deleteProject(Long projectId) {
        Project project = findProject(projectId);
        projectRepository.delete(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listMembers(Long projectId) {
        findProject(projectId);
        return projectMembershipRepository.findByProjectId(projectId).stream()
                .map(ProjectMemberResponse::from)
                .toList();
    }

    /**
     * Users not yet a member of this project, as a minimal id/username/email
     * projection - what {@code AddMemberDialog}-style UIs need to let a project's
     * Super Admin (who does not and should not hold {@code USER_READ}) pick someone
     * to add, without exposing the full account list {@code GET /api/users} does.
     */
    @Transactional(readOnly = true)
    public List<CandidateUserResponse> listCandidateUsers(Long projectId) {
        findProject(projectId);
        Set<Long> memberUserIds = projectMembershipRepository.findByProjectId(projectId).stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());
        return userRepository.findAll().stream()
                .filter(user -> !memberUserIds.contains(user.getId()))
                .map(CandidateUserResponse::from)
                .toList();
    }

    @Transactional
    public ProjectMemberResponse addMember(Long projectId, AddProjectMemberRequest request, boolean callerIsMasterAdmin) {
        Project project = findProject(projectId);

        if (projectMembershipRepository.existsByProjectIdAndUserId(projectId, request.userId())) {
            throw new DuplicateResourceException("User " + request.userId() + " is already a member of this project");
        }

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.userId()));

        Set<Role> roles = resolveRoles(request.roles());
        requireMasterAdminForSuperAdminChanges(roles, Set.of(), callerIsMasterAdmin);

        ProjectMembership membership = ProjectMembership.builder()
                .project(project)
                .user(user)
                .roles(roles)
                .build();

        return ProjectMemberResponse.from(projectMembershipRepository.save(membership));
    }

    /** Replaces (not merges with) a member's role set within this project. */
    @Transactional
    public ProjectMemberResponse updateMemberRoles(Long projectId, Long userId, UpdateProjectMemberRolesRequest request, boolean callerIsMasterAdmin) {
        ProjectMembership membership = findMembership(projectId, userId);
        Set<Role> newRoles = resolveRoles(request.roles());
        requireMasterAdminForSuperAdminChanges(newRoles, membership.getRoles(), callerIsMasterAdmin);

        membership.setRoles(newRoles);
        return ProjectMemberResponse.from(projectMembershipRepository.save(membership));
    }

    @Transactional
    public void removeMember(Long projectId, Long userId, boolean callerIsMasterAdmin) {
        ProjectMembership membership = findMembership(projectId, userId);
        requireMasterAdminForSuperAdminChanges(Set.of(), membership.getRoles(), callerIsMasterAdmin);
        projectMembershipRepository.delete(membership);
    }

    /**
     * Only a Master Admin may add, keep-while-changing-other-roles, or remove the
     * SUPER_ADMIN role on a membership - i.e. if SUPER_ADMIN appears in either the
     * before or after role set and the caller isn't a Master Admin, reject. This is
     * what stops a project's own Super Admin from minting a rival, demoting
     * themselves/others out of it, or being removed by a peer.
     */
    private void requireMasterAdminForSuperAdminChanges(Set<Role> newRoles, Set<Role> oldRoles, boolean callerIsMasterAdmin) {
        if (callerIsMasterAdmin) {
            return;
        }
        boolean touchesSuperAdmin = containsSuperAdmin(newRoles) || containsSuperAdmin(oldRoles);
        if (touchesSuperAdmin) {
            throw new AccessDeniedException("Only a Master Admin can assign or remove the SUPER_ADMIN role");
        }
    }

    private boolean containsSuperAdmin(Set<Role> roles) {
        return roles.stream().anyMatch(r -> r.getName().equals(SUPER_ADMIN_ROLE));
    }

    private Set<String> roleNames(ProjectMembership membership) {
        return membership.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
    }

    private ProjectMembership findMembership(Long projectId, Long userId) {
        return projectMembershipRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " is not a member of project " + projectId));
    }

    private Set<Role> resolveRoles(Set<String> roleNames) {
        return roleNames.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + name)))
                .collect(Collectors.toCollection(HashSet::new));
    }
}
