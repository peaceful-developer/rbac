package com.iam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iam.dto.request.LoginRequest;
import com.iam.dto.request.RefreshTokenRequest;
import com.iam.dto.request.RegisterRequest;
import com.iam.support.TestDataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDataSeeder testDataSeeder;

    @BeforeEach
    void setUp() {
        testDataSeeder.seedBaselineRolesAndPermissions();
    }

    @Test
    void registerCreatesUserAndReturnsTokens() throws Exception {
        RegisterRequest request = new RegisterRequest("jdoe", "jdoe@example.com", "SuperSecret1", "John", "Doe");

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void registerRejectsDuplicateUsername() throws Exception {
        RegisterRequest request = new RegisterRequest("jdoe2", "jdoe2@example.com", "SuperSecret1", "John", "Doe");
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithValidCredentialsReturnsTokens() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("alice", "alice@example.com", "SuperSecret1", "Alice", "A");
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("alice", "SuperSecret1");
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void loginWithBadPasswordReturnsUnauthorized() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("bob", "bob@example.com", "SuperSecret1", "Bob", "B");
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("bob", "WrongPassword");
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokenAndOldTokenBecomesInvalid() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("carol", "carol@example.com", "SuperSecret1", "Carol", "C");
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String refreshToken = objectMapper.readTree(response).get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").value(org.hamcrest.Matchers.not(refreshToken)));

        // the old (now-rotated) refresh token must be rejected on reuse
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointRejectsMissingToken() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
