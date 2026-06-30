package com.banking.controller;

import com.banking.dto.request.LoanApplicationRequest;
import com.banking.dto.request.LoanRepaymentRequest;
import com.banking.dto.response.ApiResponse;
import com.banking.dto.response.LoanResponse;
import com.banking.dto.response.PageResponse;
import com.banking.entity.User;
import com.banking.security.SecurityUtils;
import com.banking.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/loans")
@RequiredArgsConstructor
@Tag(name = "Loans", description = "Loan applications and management")
@SecurityRequirement(name = "bearerAuth")
public class LoanController {

    private final LoanService loanService;

    @PostMapping("/apply")
    @Operation(summary = "Apply for a loan")
    public ResponseEntity<ApiResponse<LoanResponse>> applyForLoan(
            @Valid @RequestBody LoanApplicationRequest request) {
        User user = SecurityUtils.getCurrentUser();
        LoanResponse loan = loanService.applyForLoan(request, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Loan application submitted successfully", loan));
    }

    @GetMapping
    @Operation(summary = "Get all loans for the current user")
    public ResponseEntity<ApiResponse<PageResponse<LoanResponse>>> getMyLoans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(loanService.getUserLoans(user.getId(), page, size)));
    }

    @GetMapping("/{loanId}")
    @Operation(summary = "Get loan details by ID")
    public ResponseEntity<ApiResponse<LoanResponse>> getLoan(@PathVariable UUID loanId) {
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(loanService.getLoan(loanId, user.getId())));
    }

    @PostMapping("/repay")
    @Operation(summary = "Make a loan repayment (OTP required)")
    public ResponseEntity<ApiResponse<LoanResponse>> repayLoan(
            @Valid @RequestBody LoanRepaymentRequest request) {
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Repayment successful",
                loanService.repayLoan(request, user)));
    }

    @GetMapping("/emi-calculator")
    @Operation(summary = "Calculate EMI for given loan parameters")
    public ResponseEntity<ApiResponse<Map<String, Object>>> calculateEmi(
            @RequestParam BigDecimal principal,
            @RequestParam BigDecimal annualRate,
            @RequestParam int tenureMonths) {
        BigDecimal emi = loanService.calculateEmi(principal, annualRate, tenureMonths);
        BigDecimal totalPayable = emi.multiply(BigDecimal.valueOf(tenureMonths));
        BigDecimal totalInterest = totalPayable.subtract(principal);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "principal", principal,
                "annualInterestRate", annualRate,
                "tenureMonths", tenureMonths,
                "monthlyEmi", emi,
                "totalInterest", totalInterest,
                "totalPayable", totalPayable
        )));
    }

    @PostMapping("/{loanId}/approve")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Approve a pending loan (Manager/Admin only)")
    public ResponseEntity<ApiResponse<LoanResponse>> approveLoan(@PathVariable UUID loanId) {
        User approver = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Loan approved",
                loanService.approveLoan(loanId, approver)));
    }

    @PostMapping("/{loanId}/reject")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Reject a pending loan (Manager/Admin only)")
    public ResponseEntity<ApiResponse<LoanResponse>> rejectLoan(@PathVariable UUID loanId) {
        User approver = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Loan rejected",
                loanService.rejectLoan(loanId, approver)));
    }

    @PostMapping("/{loanId}/disburse")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Disburse an approved loan (Manager/Admin only)")
    public ResponseEntity<ApiResponse<LoanResponse>> disburseLoan(@PathVariable UUID loanId) {
        User approver = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Loan disbursed",
                loanService.disburseLoan(loanId, approver)));
    }
}
