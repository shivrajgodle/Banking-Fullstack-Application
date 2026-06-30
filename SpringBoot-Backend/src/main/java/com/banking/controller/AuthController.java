package com.banking.controller;

import com.banking.dto.request.*;
import com.banking.dto.response.ApiResponse;
import com.banking.dto.response.AuthResponse;
import com.banking.dto.response.UserResponse;
import com.banking.entity.User;
import com.banking.security.SecurityUtils;
import com.banking.service.AuthService;
import com.banking.service.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication operations.
 * Base path: /api/v1/auth
 *
 * All endpoints in this controller (except /me and /logout) are publicly accessible —
 * they are listed in SecurityConfig.PUBLIC_ENDPOINTS and excluded from JWT filtering.
 *
 * Endpoints:
 *  POST /auth/register  — create a new customer account (201 Created)
 *  POST /auth/login     — authenticate and receive JWT token pair (200 OK)
 *  POST /auth/refresh   — exchange a refresh token for a new access token (200 OK)
 *  POST /auth/logout    — revoke refresh tokens for the current user (200 OK)
 *  POST /auth/otp/send  — request an OTP for the current user (200 OK)
 *  GET  /auth/me        — get the current user's profile (200 OK)
 *
 * All responses are wrapped in ApiResponse<T> which adds a "success" flag,
 * a message string, and a timestamp alongside the actual data payload.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, token management")
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;

    /**
     * Registers a new customer.
     *
     * @Valid triggers Jakarta validation on RegisterRequest fields
     * (e.g. @NotBlank on email, @Size on password).
     * Returns 201 Created on success rather than the default 200.
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new customer")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful. Please verify your email.", user));
    }

    /**
     * Authenticates the user and returns an access token + refresh token.
     * The access token should be included in subsequent requests as:
     *   Authorization: Bearer <accessToken>
     */
    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    /**
     * Issues a new access token using a valid refresh token.
     * The old refresh token is revoked (rotation) and a new one is returned.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }

    /**
     * Revokes all refresh tokens for the current user.
     * The access token remains valid until its natural expiry (typically 15 minutes).
     * Requires the user to be authenticated (uses SecurityUtils.getCurrentUser()).
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout and revoke refresh tokens")
    public ResponseEntity<ApiResponse<Void>> logout() {
        User user = SecurityUtils.getCurrentUser(); // extracts user from JWT
        authService.logout(user);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    /**
     * Generates and emails an OTP to the current user.
     * The OtpType in the request body determines the operation the OTP will authorise
     * (e.g. TRANSACTION, BENEFICIARY_ADD, RESET_PASSWORD).
     */
    @PostMapping("/otp/send")
    @Operation(summary = "Send OTP to the authenticated user's registered email")
    public ResponseEntity<ApiResponse<Void>> sendOtp(@Valid @RequestBody OtpRequest request) {
        User user = SecurityUtils.getCurrentUser();
        otpService.generateAndSend(user, request.getOtpType());
        return ResponseEntity.ok(ApiResponse.success("OTP sent to your registered email"));
    }

    /**
     * Returns the currently authenticated user's profile (from the JWT principal).
     * Useful for the frontend to refresh user data without a separate /users/me call.
     */
    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(authService.toUserResponse(user)));
    }
}
