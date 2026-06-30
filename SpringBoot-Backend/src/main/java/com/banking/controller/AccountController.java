package com.banking.controller;

import com.banking.dto.request.CreateAccountRequest;
import com.banking.dto.response.AccountResponse;
import com.banking.dto.response.ApiResponse;
import com.banking.dto.response.DashboardResponse;
import com.banking.entity.User;
import com.banking.security.SecurityUtils;
import com.banking.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for bank account management.
 * Base path: /api/v1/accounts
 * All endpoints require a valid Bearer JWT (@SecurityRequirement).
 *
 * Endpoints:
 *  POST   /accounts                        — open a new account (any authenticated user)
 *  GET    /accounts                        — list own accounts
 *  GET    /accounts/{accountNumber}        — get account details (owner only)
 *  GET    /accounts/dashboard              — financial summary dashboard
 *  POST   /accounts/{accountNumber}/close  — close own account (zero-balance required)
 *  POST   /accounts/{accountNumber}/freeze — freeze account (Admin/Manager only)
 *  POST   /accounts/{accountNumber}/unfreeze — restore account (Admin/Manager only)
 */
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Bank account management")
@SecurityRequirement(name = "bearerAuth") // all endpoints need JWT
public class AccountController {

    private final AccountService accountService;

    /**
     * Opens a new bank account for the authenticated user.
     * Returns 201 Created with the new account details.
     * The user is limited to 5 accounts total (enforced in AccountService).
     */
    @PostMapping
    @Operation(summary = "Open a new bank account")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        User user = SecurityUtils.getCurrentUser();
        AccountResponse account = accountService.createAccount(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created successfully", account));
    }

    /**
     * Returns all accounts owned by the authenticated user (all statuses included).
     */
    @GetMapping
    @Operation(summary = "Get all accounts for the current user")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getMyAccounts() {
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(accountService.getUserAccounts(user.getId())));
    }

    /**
     * Returns details for a specific account.
     * AccountService enforces that the requesting user owns the account (403 otherwise).
     */
    @GetMapping("/{accountNumber}")
    @Operation(summary = "Get account details by account number")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(@PathVariable String accountNumber) {
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(
                accountService.getAccountByNumber(accountNumber, user.getId())));
    }

    /**
     * Returns an aggregated financial dashboard:
     * total balance, active loans, active FDs, and 10 most recent transactions.
     */
    @GetMapping("/dashboard")
    @Operation(summary = "Get financial dashboard summary")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(accountService.getDashboard(user)));
    }

    /**
     * Closes the account. Requires zero balance — AccountService enforces this.
     * Only the account owner can close their own account.
     */
    @PostMapping("/{accountNumber}/close")
    @Operation(summary = "Close an account (zero balance required)")
    public ResponseEntity<ApiResponse<AccountResponse>> closeAccount(@PathVariable String accountNumber) {
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Account closed",
                accountService.closeAccount(accountNumber, user.getId())));
    }

    /**
     * Freezes an account — prevents all transactions.
     * @PreAuthorize restricts this to ADMIN and MANAGER roles.
     * (SecurityConfig also restricts at the URL level for defense-in-depth.)
     */
    @PostMapping("/{accountNumber}/freeze")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Freeze an account (Admin/Manager only)")
    public ResponseEntity<ApiResponse<AccountResponse>> freezeAccount(@PathVariable String accountNumber) {
        User admin = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Account frozen",
                accountService.freezeAccount(accountNumber, admin.getId())));
    }

    /**
     * Unfreezes a previously frozen account, restoring it to ACTIVE status.
     * Admin/Manager only.
     */
    @PostMapping("/{accountNumber}/unfreeze")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Unfreeze an account (Admin/Manager only)")
    public ResponseEntity<ApiResponse<AccountResponse>> unfreezeAccount(@PathVariable String accountNumber) {
        User admin = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Account unfrozen",
                accountService.unfreezeAccount(accountNumber, admin.getId())));
    }
}
