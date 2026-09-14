package com.iam.controller;

import com.iam.dto.request.AssignPermissionsRequest;
import com.iam.dto.request.CreateRoleRequest;
import com.iam.dto.request.UpdateRoleRequest;
import com.iam.dto.response.RoleResponse;
import com.iam.service.RoleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Role administration - {@code ROLE_READ}/{@code ROLE_WRITE}/{@code ROLE_DELETE}-gated.
 * There is no self-service surface here (unlike UserController): only admins with the
 * relevant permission can see or change role definitions.
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Role administration")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public ResponseEntity<List<RoleResponse>> listRoles() {
        return ResponseEntity.ok(roleService.listRoles().stream().map(RoleResponse::from).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public ResponseEntity<RoleResponse> getRole(@PathVariable Long id) {
        return ResponseEntity.ok(RoleResponse.from(roleService.getById(id)));
    }

    /** Creates a role with an optional starting permission set - see CreateRoleRequest. */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(RoleResponse.from(roleService.createRole(request)));
    }

    /** Only the description is mutable here - a role's name and permissions are set at creation/via the dedicated endpoints respectively. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    public ResponseEntity<RoleResponse> updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(RoleResponse.from(roleService.updateRole(id, request)));
    }

    /**
     * Replaces (not merges with) the role's entire permission set. This is the "super
     * admin builds a role" workflow: create the role, then call this to grant it
     * whatever permissions it needs - which immediately changes what every user
     * holding this role can do (see RoleService for the cache-eviction behavior).
     */
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    public ResponseEntity<RoleResponse> assignPermissions(@PathVariable Long id, @Valid @RequestBody AssignPermissionsRequest request) {
        return ResponseEntity.ok(RoleResponse.from(roleService.assignPermissions(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_DELETE')")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }
}
