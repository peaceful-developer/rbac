package com.iam.service;

import com.iam.config.CacheConfig;
import com.iam.domain.Permission;
import com.iam.domain.Role;
import com.iam.dto.request.AssignPermissionsRequest;
import com.iam.dto.request.CreateRoleRequest;
import com.iam.dto.request.UpdateRoleRequest;
import com.iam.exception.DuplicateResourceException;
import com.iam.exception.ResourceNotFoundException;
import com.iam.repository.PermissionRepository;
import com.iam.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Role/permission changes affect every user holding that role, and the cached
 * UserPrincipals (see CacheConfig) don't know which ones. Rather than tracking that,
 * these mutations just clear the whole cache - an infrequent admin action trading a
 * brief burst of cache misses for correctness.
 * <p>
 * Every mutating method here takes a {@code callerIsMasterAdmin} flag (passed through
 * from the controller's {@code @AuthenticationPrincipal}) rather than depending on
 * Spring Security types directly - see {@link #createRole} and the
 * {@link #requireEditable} check for how it's used to enforce the locked-role rule
 * (see {@code Role#isEditable}).
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final org.springframework.cache.CacheManager cacheManager;

    @Transactional(readOnly = true)
    public List<Role> listRoles() {
        return roleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Role getById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
    }

    /**
     * Creates a role with an optional starting permission set (empty set if none given
     * - see {@link #resolvePermissions}). A role created by a Master Admin comes out
     * locked ({@code editable = false}, see {@link Role#isEditable}); anyone else's
     * (e.g. a project Super Admin building a role from the permission catalog) comes
     * out editable. This isn't client-controlled - it's derived entirely from who's
     * making the call, so there's no separate permission check needed to prevent a
     * non-Master-Admin from minting a locked role.
     */
    @Transactional
    public Role createRole(CreateRoleRequest request, boolean callerIsMasterAdmin) {
        if (roleRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Role '" + request.name() + "' already exists");
        }

        Role role = Role.builder()
                .name(request.name())
                .description(request.description())
                .editable(!callerIsMasterAdmin)
                .permissions(resolvePermissions(request.permissions()))
                .build();

        return roleRepository.save(role);
    }

    @Transactional
    public Role updateRole(Long id, UpdateRoleRequest request, boolean callerIsMasterAdmin) {
        Role role = getById(id);
        requireEditable(role, callerIsMasterAdmin);
        if (request.description() != null) {
            role.setDescription(request.description());
        }
        return roleRepository.save(role);
    }

    /** Replaces (not merges with) the role's entire permission set, then clears the auth cache - see the class-level note above. */
    @Transactional
    public Role assignPermissions(Long id, AssignPermissionsRequest request, boolean callerIsMasterAdmin) {
        Role role = getById(id);
        requireEditable(role, callerIsMasterAdmin);
        role.setPermissions(resolvePermissions(request.permissions()));
        Role saved = roleRepository.save(role);
        clearUserDetailsCache();
        return saved;
    }

    @Transactional
    public void deleteRole(Long id, boolean callerIsMasterAdmin) {
        Role role = getById(id);
        requireEditable(role, callerIsMasterAdmin);
        roleRepository.delete(role);
        clearUserDetailsCache();
    }

    /** Enforces the lock on non-editable roles (e.g. the seeded ADMIN/SUPER_ADMIN) - only a Master Admin may bypass it. */
    private void requireEditable(Role role, boolean callerIsMasterAdmin) {
        if (!role.isEditable() && !callerIsMasterAdmin) {
            throw new AccessDeniedException("Role '" + role.getName() + "' is locked and can only be modified by a Master Admin");
        }
    }

    private void clearUserDetailsCache() {
        var cache = cacheManager.getCache(CacheConfig.USER_DETAILS_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }

    /** Looks up each requested permission by name (every name must already exist) - empty/null input yields an empty set, not an error. */
    private Set<Permission> resolvePermissions(Set<String> permissionNames) {
        if (permissionNames == null || permissionNames.isEmpty()) {
            return new HashSet<>();
        }
        return permissionNames.stream()
                .map(name -> permissionRepository.findByName(name)
                        .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + name)))
                .collect(Collectors.toCollection(HashSet::new));
    }
}
