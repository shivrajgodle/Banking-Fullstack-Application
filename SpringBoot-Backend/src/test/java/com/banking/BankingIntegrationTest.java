package com.banking;

import com.banking.dto.request.LoginRequest;
import com.banking.dto.request.RegisterRequest;
import com.banking.dto.response.AuthResponse;
import com.banking.dto.response.UserResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Banking API Integration Tests")
class BankingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("Full registration flow should return 201")
    void register_EndToEnd_Returns201() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Test");
        request.setLastName("User");
        request.setEmail("testuser.integration@example.com");
        request.setPhoneNumber("9123456780");
        request.setPassword("Test@1234");
        request.setDateOfBirth(LocalDate.of(1992, 6, 15));
        request.setNationalId("999888777666");
        request.setCity("Pune");
        request.setState("Maharashtra");
        request.setCountry("India");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("testuser.integration@example.com"))
                .andExpect(jsonPath("$.data.firstName").value("Test"));
    }

    @Test
    @DisplayName("Duplicate email registration should return 409")
    void register_DuplicateEmail_Returns409() throws Exception {
        // First registration
        RegisterRequest req1 = buildRegisterRequest("dup@example.com", "9000000001", "111122223333");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Duplicate attempt
        RegisterRequest req2 = buildRegisterRequest("dup@example.com", "9000000002", "444455556666");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_ENTRY"));
    }

    @Test
    @DisplayName("Invalid request body should return 400 with validation errors")
    void register_MissingFields_Returns400() throws Exception {
        RegisterRequest invalid = new RegisterRequest();
        invalid.setEmail("not-an-email");
        invalid.setPassword("weak");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.validationErrors").isNotEmpty());
    }

    @Test
    @DisplayName("Login with correct credentials should return JWT")
    void login_ValidCredentials_ReturnsJwt() throws Exception {
        // Register first
        RegisterRequest reg = buildRegisterRequest("logintest@example.com", "9111111111", "222233334444");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        // Login
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("logintest@example.com");
        loginRequest.setPassword("Test@1234");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("accessToken");
    }

    @Test
    @DisplayName("Login with wrong password should return 401")
    void login_WrongPassword_Returns401() throws Exception {
        RegisterRequest reg = buildRegisterRequest("wrongpass@example.com", "9222222222", "555566667777");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        LoginRequest bad = new LoginRequest();
        bad.setEmail("wrongpass@example.com");
        bad.setPassword("WrongPassword@1");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint without JWT should return 403")
    void protectedEndpoint_NoToken_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Full flow: register -> login -> get profile")
    void fullFlow_RegisterLoginGetProfile() throws Exception {
        // Register
        RegisterRequest reg = buildRegisterRequest("fullflow@example.com", "9333333333", "888899990000");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        // Login and capture token
        LoginRequest login = new LoginRequest();
        login.setEmail("fullflow@example.com");
        login.setPassword("Test@1234");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        String loginBody = loginResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(loginBody)
                .path("data").path("accessToken").asText();

        // Get profile with token
        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("fullflow@example.com"))
                .andExpect(jsonPath("$.data.firstName").value("Full"));
    }

    // ---- Helper ----
    private RegisterRequest buildRegisterRequest(String email, String phone, String nationalId) {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Full");
        req.setLastName("Flow");
        req.setEmail(email);
        req.setPhoneNumber(phone);
        req.setPassword("Test@1234");
        req.setDateOfBirth(LocalDate.of(1990, 3, 20));
        req.setNationalId(nationalId);
        req.setCity("Bangalore");
        req.setState("Karnataka");
        req.setCountry("India");
        return req;
    }
}
