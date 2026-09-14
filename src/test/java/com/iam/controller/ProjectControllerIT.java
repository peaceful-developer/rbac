package com.iam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iam.domain.Role;
import com.iam.domain.User;
import com.iam.dto.request.*;
import com.iam.repository.RoleRepository;
import com.iam.repository.UserRepository;
import com.iam.support.TestDataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Exercises the Master Admin / Project hierarchy: a Master Admin creates a project and
 * assigns its Super Admin; that Super Admin can then add ordinary members but not
 * mint a rival Super Admin, and has no authority over an unrelated project. Also
 * verifies the locked-role rule (Master-Admin-created roles can't be touched by
 * anyone else, even an ADMIN-role holder).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDataSeeder testDataSeeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        testDataSeeder.seedBaselineRolesAndPermissions();
    }

    private String registerPlainUser(String username) throws Exception {
        RegisterRequest request = new RegisterRequest(username, username + "@example.com", "SuperSecret1", "Test", "User");
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    /** A Master Admin with no other roles - proves the flag alone is sufficient, independent of the legacy global ADMIN role. */
    private String createMasterAdminAndLogin(String username) throws Exception {
        User masterAdmin = User.builder()
                .username(username)
                .email(username + "@example.com")
                .passwordHash(passwordEncoder.encode("SuperSecret1"))
                .roles(Set.of())
                .masterAdmin(true)
                .build();
        userRepository.save(masterAdmin);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "SuperSecret1"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private Long currentUserId(String token) throws Exception {
        String response = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long createProject(String masterAdminToken, String name) throws Exception {
        String response = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + masterAdminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateProjectRequest(name, "A test project"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void masterAdminOnboardsProjectAndSuperAdminManagesItsMembers() throws Exception {
        String masterAdminToken = createMasterAdminAndLogin("boss");
        Long projectId = createProject(masterAdminToken, "Acme Corp");

        String superAdminToken = registerPlainUser("projsuper");
        Long superAdminUserId = currentUserId(superAdminToken);

        // Only a Master Admin can install a project's Super Admin.
        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + masterAdminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AddProjectMemberRequest(superAdminUserId, Set.of("SUPER_ADMIN")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("SUPER_ADMIN"));

        // The Super Admin can add ordinary members to their own project.
        String memberToken = registerPlainUser("projmember");
        Long memberUserId = currentUserId(memberToken);

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AddProjectMemberRequest(memberUserId, Set.of("MANAGER")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("MANAGER"));

        // ...but cannot mint a rival Super Admin.
        String rivalToken = registerPlainUser("rival");
        Long rivalUserId = currentUserId(rivalToken);

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AddProjectMemberRequest(rivalUserId, Set.of("SUPER_ADMIN")))))
                .andExpect(status().isForbidden());

        // ...nor demote themselves out of the role.
        mockMvc.perform(put("/api/projects/" + projectId + "/members/" + superAdminUserId + "/roles")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdateProjectMemberRolesRequest(Set.of("MANAGER")))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + masterAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void superAdminCanPickCandidateUsersWithoutGlobalUserReadAccess() throws Exception {
        String masterAdminToken = createMasterAdminAndLogin("boss5");
        Long projectId = createProject(masterAdminToken, "Candidate Corp");

        String superAdminToken = registerPlainUser("candsuper");
        Long superAdminUserId = currentUserId(superAdminToken);
        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + masterAdminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AddProjectMemberRequest(superAdminUserId, Set.of("SUPER_ADMIN")))))
                .andExpect(status().isCreated());

        String candidateToken = registerPlainUser("candmember");
        currentUserId(candidateToken);

        // A plain, self-registered Super Admin holds no global USER_READ - the full user list stays forbidden...
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isForbidden());

        // ...but they can still pick who to add to their own project via the narrower, project-scoped endpoint.
        mockMvc.perform(get("/api/projects/" + projectId + "/candidate-users")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username=='candmember')]").exists())
                .andExpect(jsonPath("$[?(@.username=='candsuper')]").doesNotExist());
    }

    @Test
    void superAdminCanReadAndBuildRolesFromGlobalCatalogViaProjectMembershipAlone() throws Exception {
        String masterAdminToken = createMasterAdminAndLogin("boss6");
        Long projectId = createProject(masterAdminToken, "Role Builder Corp");

        String superAdminToken = registerPlainUser("rolesuper");
        Long superAdminUserId = currentUserId(superAdminToken);
        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + masterAdminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AddProjectMemberRequest(superAdminUserId, Set.of("SUPER_ADMIN")))))
                .andExpect(status().isCreated());

        // Their SUPER_ADMIN role only ever exists as a project membership row, never a
        // global User.roles entry - so it carries no ROLE_READ/ROLE_WRITE JWT authority.
        // The global role/permission catalog must still be reachable, since it's shared
        // across every project and this Super Admin needs it to pick/build roles for
        // their own project's members.
        mockMvc.perform(get("/api/roles").header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/permissions").header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk());

        // They can build a new (unlocked) role from that catalog...
        String createResponse = mockMvc.perform(post("/api/roles")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateRoleRequest("PROJECT_REPORTER", "Read-only reporter", Set.of()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.editable").value(true))
                .andReturn().getResponse().getContentAsString();
        Long newRoleId = objectMapper.readTree(createResponse).get("id").asLong();

        // ...but still can't touch a locked, Master-Admin-owned role.
        mockMvc.perform(put("/api/roles/" + newRoleId)
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest("Reporter, updated"))))
                .andExpect(status().isOk());

        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        mockMvc.perform(put("/api/roles/" + adminRole.getId())
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest("Attempted takeover"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminOfOneProjectHasNoAuthorityOverAnother() throws Exception {
        String masterAdminToken = createMasterAdminAndLogin("boss2");
        Long projectA = createProject(masterAdminToken, "Project A");
        Long projectB = createProject(masterAdminToken, "Project B");

        String superAdminAToken = registerPlainUser("superA");
        Long superAdminAId = currentUserId(superAdminAToken);

        mockMvc.perform(post("/api/projects/" + projectA + "/members")
                        .header("Authorization", "Bearer " + masterAdminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AddProjectMemberRequest(superAdminAId, Set.of("SUPER_ADMIN")))))
                .andExpect(status().isCreated());

        String otherUserToken = registerPlainUser("otheruser");
        Long otherUserId = currentUserId(otherUserToken);

        // Project A's Super Admin has no say over Project B.
        mockMvc.perform(post("/api/projects/" + projectB + "/members")
                        .header("Authorization", "Bearer " + superAdminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AddProjectMemberRequest(otherUserId, Set.of("MANAGER")))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/projects/" + projectB + "/members")
                        .header("Authorization", "Bearer " + superAdminAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void lockedRoleCanOnlyBeChangedByMasterAdmin() throws Exception {
        String masterAdminToken = createMasterAdminAndLogin("boss3");

        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();

        // A regular ADMIN-role (but non-Master-Admin) user cannot modify the locked ADMIN role.
        User plainAdmin = User.builder()
                .username("plainadmin")
                .email("plainadmin@example.com")
                .passwordHash(passwordEncoder.encode("SuperSecret1"))
                .roles(Set.of(adminRole))
                .build();
        userRepository.save(plainAdmin);
        String plainAdminToken = objectMapper.readTree(
                mockMvc.perform(post("/api/auth/login")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(new LoginRequest("plainadmin", "SuperSecret1"))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()
        ).get("accessToken").asText();

        mockMvc.perform(put("/api/roles/" + adminRole.getId())
                        .header("Authorization", "Bearer " + plainAdminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest("Attempted edit"))))
                .andExpect(status().isForbidden());

        // A Master Admin, however, can.
        mockMvc.perform(put("/api/roles/" + adminRole.getId())
                        .header("Authorization", "Bearer " + masterAdminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest("Updated by master admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated by master admin"));
    }

    @Test
    void onlyMasterAdminCanPromoteAnotherMasterAdmin() throws Exception {
        String masterAdminToken = createMasterAdminAndLogin("boss4");
        String plainUserToken = registerPlainUser("hopeful");
        Long plainUserId = currentUserId(plainUserToken);

        mockMvc.perform(patch("/api/users/" + plainUserId + "/master-admin")
                        .header("Authorization", "Bearer " + plainUserToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetMasterAdminRequest(true))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/users/" + plainUserId + "/master-admin")
                        .header("Authorization", "Bearer " + masterAdminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SetMasterAdminRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masterAdmin").value(true));
    }
}
