package com.banking.controller;

import com.banking.dto.request.CreateFdRequest;
import com.banking.dto.response.ApiResponse;
import com.banking.dto.response.FixedDepositResponse;
import com.banking.dto.response.PageResponse;
import com.banking.entity.User;
import com.banking.security.SecurityUtils;
import com.banking.service.FixedDepositService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/fixed-deposits")
@RequiredArgsConstructor
@Tag(name = "Fixed Deposits", description = "FD management and interest calculation")
@SecurityRequirement(name = "bearerAuth")
public class FixedDepositController {

    private final FixedDepositService fdService;

    @PostMapping
    @Operation(summary = "Create a new fixed deposit")
    public ResponseEntity<ApiResponse<FixedDepositResponse>> createFd(
            @Valid @RequestBody CreateFdRequest request) {
        User user = SecurityUtils.getCurrentUser();
        FixedDepositResponse fd = fdService.createFd(request, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Fixed deposit created successfully", fd));
    }

    @GetMapping
    @Operation(summary = "Get all fixed deposits for current user")
    public ResponseEntity<ApiResponse<PageResponse<FixedDepositResponse>>> getMyFds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(fdService.getUserFds(user.getId(), page, size)));
    }

    @PostMapping("/{fdId}/close")
    @Operation(summary = "Close FD prematurely (penalty applies)")
    public ResponseEntity<ApiResponse<FixedDepositResponse>> closePremature(@PathVariable UUID fdId) {
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("FD closed with applicable penalty",
                fdService.prematureWithdrawal(fdId, user)));
    }

    @GetMapping("/calculator")
    @Operation(summary = "Calculate FD maturity amount")
    public ResponseEntity<ApiResponse<Map<String, Object>>> calculate(
            @RequestParam BigDecimal principal,
            @RequestParam BigDecimal annualRate,
            @RequestParam int tenureMonths) {
        BigDecimal maturityAmount = fdService.calculateMaturityAmount(principal, annualRate, tenureMonths);
        BigDecimal interest = maturityAmount.subtract(principal);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "principal", principal,
                "annualInterestRate", annualRate,
                "tenureMonths", tenureMonths,
                "interestEarned", interest,
                "maturityAmount", maturityAmount
        )));
    }
}
