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
 * Exercises the Master Admin workflow end-to-end: creates a new permission, creates a
 * role carrying it, and assigns that role to another user. Also verifies a plain USER
 * is denied the same actions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacControllerIT {

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

    /** Registers a plain user via the public endpoint and returns their access token. */
    private String registerPlainUser(String username) throws Exception {
        RegisterRequest request = new RegisterRequest(username, username + "@example.com", "SuperSecret1", "Test", "User");
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    /**
     * Creates an ADMIN-role, Master Admin user directly against the repositories
     * (there is no public "become admin" endpoint by design - in real environments
     * this comes from the Flyway-seeded default admin account) and returns their
     * access token.
     */
    private String createAdminAndLogin(String username) throws Exception {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        User admin = User.builder()
                .username(username)
                .email(username + "@example.com")
                .passwordHash(passwordEncoder.encode("SuperSecret1"))
                .roles(Set.of(adminRole))
                .masterAdmin(true)
                .build();
        userRepository.save(admin);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "SuperSecret1"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    @Test
    void nonAdminCannotCreatePermissions() throws Exception {
        String userToken = registerPlainUser("plainuser");

        CreatePermissionRequest request = new CreatePermissionRequest("REPORT_VIEW", "View reports");
        mockMvc.perform(post("/api/permissions")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreatePermissionRoleAndAssignToUser() throws Exception {
        String adminToken = createAdminAndLogin("superadmin");

        // 1. create a new permission
        mockMvc.perform(post("/api/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreatePermissionRequest("REPORT_VIEW", "View reports"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("REPORT_VIEW"));

        // 2. create a role carrying that permission
        mockMvc.perform(post("/api/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateRoleRequest("REPORTER", "Can view reports", Set.of("REPORT_VIEW")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permissions[0].name").value("REPORT_VIEW"));

        // 3. register a plain user, then assign the new role to them
        String plainUserToken = registerPlainUser("reportuser");
        Long plainUserId = objectMapper.readTree(
                mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + plainUserToken))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asLong();

        mockMvc.perform(put("/api/users/" + plainUserId + "/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignRolesRequest(Set.of("REPORTER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("REPORTER"));
    }

    @Test
    void nonAdminCannotAssignRolesToOtherUsers() throws Exception {
        String userToken = registerPlainUser("regularjoe");
        Long selfId = objectMapper.readTree(
                mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + userToken))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asLong();

        mockMvc.perform(put("/api/users/" + selfId + "/roles")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new AssignRolesRequest(Set.of("ADMIN")))))
                .andExpect(status().isForbidden());
    }
}
