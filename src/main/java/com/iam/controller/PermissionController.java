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
 * Permission-catalog administration - {@code PERMISSION_READ}/{@code PERMISSION_WRITE}/
 * {@code PERMISSION_DELETE}-gated. This only manages the catalog of grantable actions
 * (name + description); attaching a permission to a role happens through
 * {@link RoleController#assignPermissions}, not here.
 */
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "Permission administration")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_READ')")
    public ResponseEntity<List<PermissionResponse>> listPermissions() {
        return ResponseEntity.ok(permissionService.listPermissions().stream().map(PermissionResponse::from).toList());
    }

    /** Registers a new permission name so it becomes selectable when building/editing a role - see RoleController. */
    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_WRITE')")
    public ResponseEntity<PermissionResponse> createPermission(@Valid @RequestBody CreatePermissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(PermissionResponse.from(permissionService.createPermission(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_DELETE')")
    public ResponseEntity<Void> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return ResponseEntity.noContent().build();
    }
}
