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
 * Seeds the baseline roles/permissions that Flyway's V2/V4 migrations provide in real
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
        Permission projectRead = permissionRepository.save(Permission.builder().name("PROJECT_READ").description("View projects").build());
        Permission projectWrite = permissionRepository.save(Permission.builder().name("PROJECT_WRITE").description("Manage projects").build());
        Permission projectDelete = permissionRepository.save(Permission.builder().name("PROJECT_DELETE").description("Delete projects").build());
        Permission projectMemberRead = permissionRepository.save(Permission.builder().name("PROJECT_MEMBER_READ").description("View project members").build());
        Permission projectMemberWrite = permissionRepository.save(Permission.builder().name("PROJECT_MEMBER_WRITE").description("Manage project members").build());
        Permission projectMemberDelete = permissionRepository.save(Permission.builder().name("PROJECT_MEMBER_DELETE").description("Remove project members").build());

        // Locked (editable=false), like the real ADMIN seed data - only a Master Admin
        // may modify it, even though it's assigned to plenty of non-master-admin test users.
        roleRepository.save(Role.builder()
                .name("ADMIN")
                .description("Full administrative access")
                .editable(false)
                .permissions(Set.of(userRead, userWrite, userDelete, roleRead, roleWrite, roleDelete,
                        permissionRead, permissionWrite, permissionDelete,
                        projectRead, projectWrite, projectDelete,
                        projectMemberRead, projectMemberWrite, projectMemberDelete))
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

        // Locked, matching the real V4 seed data - project-scoped role assigned via
        // ProjectMembership, never globally.
        roleRepository.save(Role.builder()
                .name("SUPER_ADMIN")
                .description("Manages a single project's members and roles")
                .editable(false)
                .permissions(Set.of(projectMemberRead, projectMemberWrite, projectMemberDelete,
                        roleRead, roleWrite, permissionRead))
                .build());
    }
}
