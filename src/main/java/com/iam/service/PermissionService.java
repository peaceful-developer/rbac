package com.iam.service;

import com.iam.config.CacheConfig;
import com.iam.domain.Permission;
import com.iam.dto.request.CreatePermissionRequest;
import com.iam.exception.DuplicateResourceException;
import com.iam.exception.ResourceNotFoundException;
import com.iam.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final CacheManager cacheManager;

    @Transactional(readOnly = true)
    public List<Permission> listPermissions() {
        return permissionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Permission getById(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + id));
    }

    @Transactional
    public Permission createPermission(CreatePermissionRequest request) {
        if (permissionRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Permission '" + request.name() + "' already exists");
        }
        Permission permission = Permission.builder()
                .name(request.name())
                .description(request.description())
                .build();
        return permissionRepository.save(permission);
    }

    @Transactional
    public void deletePermission(Long id) {
        Permission permission = getById(id);
        permissionRepository.delete(permission);
        var cache = cacheManager.getCache(CacheConfig.USER_DETAILS_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }
}
