package com.iam.controller;

import com.iam.dto.request.CreatePermissionRequest;
import com.iam.dto.response.PermissionResponse;
import com.iam.service.PermissionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Permission-catalog administration. Reading the catalog only requires
 * {@code PERMISSION_READ} (so e.g. a project Super Admin can see what's available
 * when building a role - see RoleController), but extending or shrinking the catalog
 * itself is Master-Admin-only, not just any {@code PERMISSION_WRITE}/
 * {@code PERMISSION_DELETE} holder - only a Master Admin defines what permissions
 * exist at all. Attaching an existing permission to a role happens through
 * {@link RoleController#assignPermissions}, not here.
 */
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "Permission administration")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_READ') or hasAuthority('MASTER_ADMIN') or @projectAuthorizationService.isSuperAdminOfAnyProject()")
    public ResponseEntity<List<PermissionResponse>> listPermissions() {
        return ResponseEntity.ok(permissionService.listPermissions().stream().map(PermissionResponse::from).toList());
    }

    /** Registers a new permission name so it becomes selectable when building/editing a role - see RoleController. */
    @PostMapping
    @PreAuthorize("hasAuthority('MASTER_ADMIN')")
    public ResponseEntity<PermissionResponse> createPermission(@Valid @RequestBody CreatePermissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(PermissionResponse.from(permissionService.createPermission(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_ADMIN')")
    public ResponseEntity<Void> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return ResponseEntity.noContent().build();
    }
}
