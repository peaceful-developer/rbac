package com.iam.controller;

import com.iam.dto.request.AddProjectMemberRequest;
import com.iam.dto.request.CreateProjectRequest;
import com.iam.dto.request.CreateProjectUserRequest;
import com.iam.dto.request.UpdateProjectMemberRolesRequest;
import com.iam.dto.request.UpdateProjectRequest;
import com.iam.dto.response.CandidateUserResponse;
import com.iam.dto.response.ProjectMemberResponse;
import com.iam.dto.response.ProjectResponse;
import com.iam.security.UserPrincipal;
import com.iam.service.ProjectService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Projects (tenants) and their membership.
 * <p>
 * Project CRUD ({@code POST}/{@code PUT}/{@code DELETE} here) is Master-Admin-only,
 * gated the same way as every other platform-level action in this app -
 * {@code hasAuthority('MASTER_ADMIN')} against the caller's globally-embedded
 * authorities (see UserPrincipal).
 * <p>
 * Membership endpoints are different: whether a caller may manage <em>this specific
 * project's</em> members can't be answered from their global authorities alone (a
 * Super Admin of project 1 must not thereby manage project 2). Those are gated via
 * {@code @projectAuthorizationService.hasProjectAuthority(#id, '...')}, which checks a
 * Master-Admin bypass first and then that user's actual membership row for this
 * project - see {@code ProjectAuthorizationService}.
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project (tenant) administration and membership")
public class ProjectController {

    private final ProjectService projectService;

    /** No permission gate - every authenticated user may call this; the result itself is filtered to what they're allowed to see (see ProjectService#listProjects). */
    @GetMapping
    public ResponseEntity<List<ProjectResponse>> listProjects(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(projectService.listProjects(principal));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_ADMIN') or @projectAuthorizationService.isProjectMember(#id)")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(projectService.getProject(id, principal));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MASTER_ADMIN')")
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.createProject(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_ADMIN')")
    public ResponseEntity<ProjectResponse> updateProject(@PathVariable Long id, @Valid @RequestBody UpdateProjectRequest request) {
        return ResponseEntity.ok(projectService.updateProject(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_ADMIN')")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("@projectAuthorizationService.hasProjectAuthority(#id, 'PROJECT_MEMBER_READ')")
    public ResponseEntity<List<ProjectMemberResponse>> listMembers(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.listMembers(id));
    }

    /**
     * Users not yet in this project, for an "add member" picker - deliberately a
     * separate, narrower endpoint from {@code GET /api/users} (which requires
     * {@code USER_READ}, an authority a project's Super Admin does not hold and
     * should not need just to add someone to their own project).
     */
    @GetMapping("/{id}/candidate-users")
    @PreAuthorize("@projectAuthorizationService.hasProjectAuthority(#id, 'PROJECT_MEMBER_WRITE')")
    public ResponseEntity<List<CandidateUserResponse>> listCandidateUsers(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.listCandidateUsers(id));
    }

    /**
     * Creates a brand-new account directly into this project - what a tenant's Super
     * Admin needs to onboard their own staff, who by definition don't have accounts
     * yet. {@code POST /api/members} above can only pick an existing account, and
     * {@code POST /api/users} (global, {@code USER_WRITE}-gated) is out of a Super
     * Admin's reach by design, so without this a Super Admin could only ever add
     * people who had already self-registered.
     * <p>
     * Same gate as adding a member, and the same SUPER_ADMIN restriction applies
     * inside ProjectService - a Super Admin can't bootstrap a rival this way either.
     */
    @PostMapping("/{id}/users")
    @PreAuthorize("@projectAuthorizationService.hasProjectAuthority(#id, 'PROJECT_MEMBER_WRITE')")
    public ResponseEntity<ProjectMemberResponse> createUserInProject(@PathVariable Long id,
                                                                      @Valid @RequestBody CreateProjectUserRequest request,
                                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.createUserInProject(id, request, principal.isMasterAdmin()));
    }

    /** Assigning the SUPER_ADMIN role here is further restricted to Master Admins inside ProjectService, beyond what this permission check alone allows. */
    @PostMapping("/{id}/members")
    @PreAuthorize("@projectAuthorizationService.hasProjectAuthority(#id, 'PROJECT_MEMBER_WRITE')")
    public ResponseEntity<ProjectMemberResponse> addMember(@PathVariable Long id,
                                                            @Valid @RequestBody AddProjectMemberRequest request,
                                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.addMember(id, request, principal.isMasterAdmin()));
    }

    /** Replaces (not merges with) the member's role set; see ProjectService for the SUPER_ADMIN restriction. */
    @PutMapping("/{id}/members/{userId}/roles")
    @PreAuthorize("@projectAuthorizationService.hasProjectAuthority(#id, 'PROJECT_MEMBER_WRITE')")
    public ResponseEntity<ProjectMemberResponse> updateMemberRoles(@PathVariable Long id,
                                                                    @PathVariable Long userId,
                                                                    @Valid @RequestBody UpdateProjectMemberRolesRequest request,
                                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(projectService.updateMemberRoles(id, userId, request, principal.isMasterAdmin()));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("@projectAuthorizationService.hasProjectAuthority(#id, 'PROJECT_MEMBER_DELETE')")
    public ResponseEntity<Void> removeMember(@PathVariable Long id,
                                              @PathVariable Long userId,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        projectService.removeMember(id, userId, principal.isMasterAdmin());
        return ResponseEntity.noContent().build();
    }
}
