package com.banking.controller;

import com.banking.dto.request.ChangePasswordRequest;
import com.banking.dto.request.UpdateProfileRequest;
import com.banking.dto.response.ApiResponse;
import com.banking.dto.response.PageResponse;
import com.banking.dto.response.UserResponse;
import com.banking.entity.User;
import com.banking.enums.KycStatus;
import com.banking.enums.UserStatus;
import com.banking.exception.BankingException;
import com.banking.repository.UserRepository;
import com.banking.security.SecurityUtils;
import com.banking.service.AuditService;
import com.banking.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile and management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/profile")
    @Operation(summary = "Get current user's profile")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile() {
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(authService.toUserResponse(user)));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update current user's profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        User user = SecurityUtils.getCurrentUser();
        if (request.getFirstName() != null)   user.setFirstName(request.getFirstName());
        if (request.getLastName() != null)    user.setLastName(request.getLastName());
        if (request.getAddress() != null)     user.setAddress(request.getAddress());
        if (request.getCity() != null)        user.setCity(request.getCity());
        if (request.getState() != null)       user.setState(request.getState());
        if (request.getZipCode() != null)     user.setZipCode(request.getZipCode());
        if (request.getCountry() != null)     user.setCountry(request.getCountry());
        if (request.getProfileImage() != null) user.setProfileImage(request.getProfileImage());
        User saved = userRepository.save(user);
        auditService.log(user.getId(), "PROFILE_UPDATED", "User", user.getId().toString());
        return ResponseEntity.ok(ApiResponse.success("Profile updated", authService.toUserResponse(saved)));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        User user = SecurityUtils.getCurrentUser();
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw BankingException.badRequest("Current password is incorrect");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw BankingException.badRequest("New password and confirm password do not match");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        auditService.log(user.getId(), "PASSWORD_CHANGED", "User", user.getId().toString());
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "List all users (Admin/Manager only)")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        Page<User> userPage = (search != null && !search.isBlank())
                ? userRepository.searchUsers(search, PageRequest.of(page, size, Sort.by("createdAt").descending()))
                : userRepository.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()));
        PageResponse<UserResponse> response = PageResponse.<UserResponse>builder()
                .content(userPage.getContent().stream().map(authService::toUserResponse).toList())
                .page(page).size(size)
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .first(userPage.isFirst()).last(userPage.isLast())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TELLER')")
    @Operation(summary = "Get user by ID (Staff only)")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BankingException.notFound("User", userId.toString()));
        return ResponseEntity.ok(ApiResponse.success(authService.toUserResponse(user)));
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Update user status (Admin/Manager only)")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(
            @PathVariable UUID userId, @RequestParam UserStatus status) {
        User admin = SecurityUtils.getCurrentUser();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BankingException.notFound("User", userId.toString()));
        user.setStatus(status);
        User saved = userRepository.save(user);
        auditService.log(admin.getId(), "USER_STATUS_UPDATED", "User", userId.toString());
        return ResponseEntity.ok(ApiResponse.success("User status updated", authService.toUserResponse(saved)));
    }

    @PatchMapping("/{userId}/kyc")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Update KYC status (Admin/Manager only)")
    public ResponseEntity<ApiResponse<UserResponse>> updateKycStatus(
            @PathVariable UUID userId, @RequestParam KycStatus kycStatus) {
        User admin = SecurityUtils.getCurrentUser();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BankingException.notFound("User", userId.toString()));
        user.setKycStatus(kycStatus);
        User saved = userRepository.save(user);
        auditService.log(admin.getId(), "KYC_UPDATED", "User", userId.toString());
        return ResponseEntity.ok(ApiResponse.success("KYC status updated", authService.toUserResponse(saved)));
    }
}
