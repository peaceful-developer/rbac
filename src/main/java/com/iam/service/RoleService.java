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

    @Transactional
    public Role createRole(CreateRoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Role '" + request.name() + "' already exists");
        }

        Role role = Role.builder()
                .name(request.name())
                .description(request.description())
                .permissions(resolvePermissions(request.permissions()))
                .build();

        return roleRepository.save(role);
    }

    @Transactional
    public Role updateRole(Long id, UpdateRoleRequest request) {
        Role role = getById(id);
        if (request.description() != null) {
            role.setDescription(request.description());
        }
        return roleRepository.save(role);
    }

    @Transactional
    public Role assignPermissions(Long id, AssignPermissionsRequest request) {
        Role role = getById(id);
        role.setPermissions(resolvePermissions(request.permissions()));
        Role saved = roleRepository.save(role);
        clearUserDetailsCache();
        return saved;
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = getById(id);
        roleRepository.delete(role);
        clearUserDetailsCache();
    }

    private void clearUserDetailsCache() {
        var cache = cacheManager.getCache(CacheConfig.USER_DETAILS_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }

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
