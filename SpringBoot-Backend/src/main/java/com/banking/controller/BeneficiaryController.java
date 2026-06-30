package com.banking.controller;

import com.banking.dto.request.AddBeneficiaryRequest;
import com.banking.dto.response.ApiResponse;
import com.banking.dto.response.BeneficiaryResponse;
import com.banking.entity.User;
import com.banking.enums.OtpType;
import com.banking.security.SecurityUtils;
import com.banking.service.BeneficiaryService;
import com.banking.service.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/beneficiaries")
@RequiredArgsConstructor
@Tag(name = "Beneficiaries", description = "Manage transfer beneficiaries")
@SecurityRequirement(name = "bearerAuth")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;
    private final OtpService otpService;

    @PostMapping("/otp")
    @Operation(summary = "Request OTP to add a beneficiary")
    public ResponseEntity<ApiResponse<Void>> requestOtp() {
        User user = SecurityUtils.getCurrentUser();
        otpService.generateAndSend(user, OtpType.BENEFICIARY_ADD);
        return ResponseEntity.ok(ApiResponse.success("OTP sent to your registered email"));
    }

    @PostMapping
    @Operation(summary = "Add a new beneficiary (OTP required)")
    public ResponseEntity<ApiResponse<BeneficiaryResponse>> addBeneficiary(
            @Valid @RequestBody AddBeneficiaryRequest request) {
        User user = SecurityUtils.getCurrentUser();
        BeneficiaryResponse beneficiary = beneficiaryService.addBeneficiary(request, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Beneficiary added successfully", beneficiary));
    }

    @GetMapping
    @Operation(summary = "Get all beneficiaries for current user")
    public ResponseEntity<ApiResponse<List<BeneficiaryResponse>>> getBeneficiaries() {
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(beneficiaryService.getBeneficiaries(user.getId())));
    }

    @DeleteMapping("/{beneficiaryId}")
    @Operation(summary = "Remove a beneficiary")
    public ResponseEntity<ApiResponse<Void>> deleteBeneficiary(@PathVariable UUID beneficiaryId) {
        User user = SecurityUtils.getCurrentUser();
        beneficiaryService.deleteBeneficiary(beneficiaryId, user.getId());
        return ResponseEntity.ok(ApiResponse.success("Beneficiary removed successfully"));
    }
}
