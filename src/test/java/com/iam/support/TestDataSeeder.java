package com.iam.support;

import com.iam.domain.Permission;
import com.iam.domain.Role;
import com.iam.repository.PermissionRepository;
import com.iam.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Seeds the baseline roles/permissions that Flyway's V2 migration provides in real
 * environments. Tests run against H2 with Flyway disabled, so this fills the same gap.
 */
@Component
public class TestDataSeeder {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Transactional
    public void seedBaselineRolesAndPermissions() {
        if (roleRepository.count() > 0) {
            return;
        }

        Permission userRead = permissionRepository.save(Permission.builder().name("USER_READ").description("View users").build());
        Permission userWrite = permissionRepository.save(Permission.builder().name("USER_WRITE").description("Manage users").build());
        Permission userDelete = permissionRepository.save(Permission.builder().name("USER_DELETE").description("Delete users").build());
        Permission roleRead = permissionRepository.save(Permission.builder().name("ROLE_READ").description("View roles").build());
        Permission roleWrite = permissionRepository.save(Permission.builder().name("ROLE_WRITE").description("Manage roles").build());
        Permission roleDelete = permissionRepository.save(Permission.builder().name("ROLE_DELETE").description("Delete roles").build());
        Permission permissionRead = permissionRepository.save(Permission.builder().name("PERMISSION_READ").description("View permissions").build());
        Permission permissionWrite = permissionRepository.save(Permission.builder().name("PERMISSION_WRITE").description("Manage permissions").build());
        Permission permissionDelete = permissionRepository.save(Permission.builder().name("PERMISSION_DELETE").description("Delete permissions").build());

        roleRepository.save(Role.builder()
                .name("ADMIN")
                .description("Full administrative access")
                .permissions(Set.of(userRead, userWrite, userDelete, roleRead, roleWrite, roleDelete,
                        permissionRead, permissionWrite, permissionDelete))
                .build());

        roleRepository.save(Role.builder()
                .name("MANAGER")
                .description("Manage users")
                .permissions(Set.of(userRead, userWrite, roleRead, permissionRead))
                .build());

        roleRepository.save(Role.builder()
                .name("USER")
                .description("Standard user")
                .permissions(Set.of())
                .build());
    }
}
