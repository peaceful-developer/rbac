package com.iam.controller;

import com.iam.dto.request.AssignPermissionsRequest;
import com.iam.dto.request.CreateRoleRequest;
import com.iam.dto.request.UpdateRoleRequest;
import com.iam.dto.response.RoleResponse;
import com.iam.security.UserPrincipal;
import com.iam.service.RoleService;
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
 * Role administration - {@code ROLE_READ}/{@code ROLE_WRITE}/{@code ROLE_DELETE}-gated
 * at this (coarse) level, with a Master Admin always let through too (see each
 * {@code @PreAuthorize}): Master Admin's authority over roles is inherent to the role
 * itself, not something that depends on separately holding these permissions (a
 * Master Admin may hold no ordinary roles at all - see ProjectControllerIT). There is
 * no self-service surface here (unlike UserController): only admins with the relevant
 * permission can see or change role definitions - though see {@link RoleService} for
 * the finer-grained rule this doesn't capture: a *locked* role (created by a Master
 * Admin, e.g. the seeded ADMIN/SUPER_ADMIN) can only be modified by a Master Admin,
 * even by someone who holds {@code ROLE_WRITE}. That's why every mutating method here
 * passes {@code principal.isMasterAdmin()} through - the permission check above is
 * necessary but not sufficient.
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Role administration")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ') or hasAuthority('MASTER_ADMIN')")
    public ResponseEntity<List<RoleResponse>> listRoles() {
        return ResponseEntity.ok(roleService.listRoles().stream().map(RoleResponse::from).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_READ') or hasAuthority('MASTER_ADMIN')")
    public ResponseEntity<RoleResponse> getRole(@PathVariable Long id) {
        return ResponseEntity.ok(RoleResponse.from(roleService.getById(id)));
    }

    /** Creates a role with an optional starting permission set - see CreateRoleRequest. Comes out locked iff the caller is a Master Admin - see RoleService#createRole. */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_WRITE') or hasAuthority('MASTER_ADMIN')")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RoleResponse.from(roleService.createRole(request, principal.isMasterAdmin())));
    }

    /** Only the description is mutable here - a role's name and permissions are set at creation/via the dedicated endpoints respectively. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_WRITE') or hasAuthority('MASTER_ADMIN')")
    public ResponseEntity<RoleResponse> updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(RoleResponse.from(roleService.updateRole(id, request, principal.isMasterAdmin())));
    }

    /**
     * Replaces (not merges with) the role's entire permission set. This is the "super
     * admin builds a role" workflow: create the role, then call this to grant it
     * whatever permissions it needs - which immediately changes what every user
     * holding this role can do (see RoleService for the cache-eviction behavior).
     */
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_WRITE') or hasAuthority('MASTER_ADMIN')")
    public ResponseEntity<RoleResponse> assignPermissions(@PathVariable Long id, @Valid @RequestBody AssignPermissionsRequest request,
                                                           @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(RoleResponse.from(roleService.assignPermissions(id, request, principal.isMasterAdmin())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_DELETE') or hasAuthority('MASTER_ADMIN')")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        roleService.deleteRole(id, principal.isMasterAdmin());
        return ResponseEntity.noContent().build();
    }
}
