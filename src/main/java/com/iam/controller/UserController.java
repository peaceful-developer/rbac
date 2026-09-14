package com.iam.controller;

import com.iam.dto.request.AssignRolesRequest;
import com.iam.dto.request.ChangePasswordRequest;
import com.iam.dto.request.CreateUserRequest;
import com.iam.dto.request.SetMasterAdminRequest;
import com.iam.dto.request.UpdateUserRequest;
import com.iam.dto.response.UserResponse;
import com.iam.security.UserPrincipal;
import com.iam.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * User administration ({@code USER_READ}/{@code USER_WRITE}/{@code USER_DELETE}-gated,
 * see the {@code @PreAuthorize} on each admin endpoint) plus two self-service endpoints
 * ({@code /me}, {@code /me/password}) available to any authenticated user regardless of
 * permissions - those two act on the caller's own account, identified via the injected
 * {@link UserPrincipal} rather than a path variable.
 * <p>
 * {@code /me} is registered before {@code /{id}} so Spring MVC matches the literal
 * path first; a numeric {@code id} would never collide with it anyway, but the
 * ordering keeps the two clearly separate here regardless.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User administration and self-service")
public class UserController {

    private final UserService userService;

    /** No @PreAuthorize - every authenticated user can view their own profile, permissions notwithstanding. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(UserResponse.from(userService.getById(principal.getId())));
    }

    /** No @PreAuthorize - self-service; see UserService#changePassword for the current-password check and session-revocation behavior. */
    @PatchMapping("/me/password")
    public ResponseEntity<Void> changeOwnPassword(@AuthenticationPrincipal UserPrincipal principal,
                                                   @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<Page<UserResponse>> listUsers(Pageable pageable) {
        return ResponseEntity.ok(userService.listUsers(pageable).map(UserResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(UserResponse.from(userService.getById(id)));
    }

    /** Admin-side creation, with an optional initial role set - see CreateUserRequest. */
    @PostMapping
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(userService.createUser(request)));
    }

    /** Partial profile/status update - see UpdateUserRequest; does not touch roles or password (those have their own endpoints). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(UserResponse.from(userService.updateUser(id, request)));
    }

    /** Replaces (not merges with) the target user's entire role set. */
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<UserResponse> assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request) {
        return ResponseEntity.ok(UserResponse.from(userService.assignRoles(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Grants or revokes the platform-level Master Admin flag. Deliberately requires
     * the caller to already be a Master Admin (not just {@code USER_WRITE}) - this is
     * how "only a Master Admin can create another Master Admin" is enforced, and it's
     * the only way this flag can ever change (there is no MASTER_ADMIN row in the
     * roles table for {@link #assignRoles} to touch).
     */
    @PatchMapping("/{id}/master-admin")
    @PreAuthorize("hasAuthority('MASTER_ADMIN')")
    public ResponseEntity<UserResponse> setMasterAdmin(@PathVariable Long id, @Valid @RequestBody SetMasterAdminRequest request) {
        return ResponseEntity.ok(UserResponse.from(userService.setMasterAdmin(id, request.masterAdmin())));
    }
}
