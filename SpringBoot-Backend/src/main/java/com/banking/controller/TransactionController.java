package com.banking.controller;

import com.banking.dto.request.DepositRequest;
import com.banking.dto.request.TransferRequest;
import com.banking.dto.request.WithdrawalRequest;
import com.banking.dto.response.ApiResponse;
import com.banking.dto.response.PageResponse;
import com.banking.dto.response.TransactionResponse;
import com.banking.entity.User;
import com.banking.security.SecurityUtils;
import com.banking.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for financial transactions.
 * Base path: /api/v1/transactions
 * All endpoints require a valid Bearer JWT.
 *
 * Endpoints:
 *  POST /transactions/deposit             — cash deposit (Teller/Admin/Manager only)
 *  POST /transactions/withdraw            — withdrawal (any user, OTP required)
 *  POST /transactions/transfer            — fund transfer (any user, OTP required)
 *  GET  /transactions/account/{number}    — paginated transaction history
 *  GET  /transactions/ref/{txnRef}        — look up a transaction by reference
 *  POST /transactions/{id}/reverse        — reverse a transaction (Admin only)
 *
 * Role enforcement summary:
 *  - DEPOSIT  : TELLER, ADMIN, MANAGER  (also enforced in SecurityConfig)
 *  - REVERSE  : ADMIN only (@PreAuthorize)
 *  - All other endpoints: any authenticated user (with ownership checks in service layer)
 */
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Deposits, withdrawals, and transfers")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Deposits cash or a cheque into an account.
     * Only tellers and management staff can perform deposits (branch counter operation).
     * @PreAuthorize + SecurityConfig both enforce the role restriction.
     *
     * @param request contains accountNumber, amount, paymentMode (CASH/CHEQUE/NEFT), description
     */
    @PostMapping("/deposit")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Cash deposit into an account (Teller/Admin/Manager only)")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @Valid @RequestBody DepositRequest request) {
        User teller = SecurityUtils.getCurrentUser(); // the teller performing the deposit
        return ResponseEntity.ok(ApiResponse.success("Deposit successful",
                transactionService.deposit(request, teller)));
    }

    /**
     * Withdraws funds from the user's own account.
     * Requires an OTP (TRANSACTION type) — must be requested first via /auth/otp/send.
     * TransactionService validates: ownership, amount, single limit, daily limit, balance.
     *
     * @param request contains accountNumber, amount, otp, paymentMode, description
     */
    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw from own account (OTP required)")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @Valid @RequestBody WithdrawalRequest request) {
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Withdrawal successful",
                transactionService.withdraw(request, user)));
    }

    /**
     * Transfers funds from the user's account to any other account.
     * Requires OTP. Creates two linked transaction records (TRANSFER_OUT + TRANSFER_IN).
     * Recipient is notified if they are a different user.
     *
     * @param request contains fromAccountNumber, toAccountNumber, amount, otp, description
     */
    @PostMapping("/transfer")
    @Operation(summary = "Transfer funds between accounts (OTP required)")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request) {
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Transfer successful",
                transactionService.transfer(request, user)));
    }

    /**
     * Returns paginated transaction history for a specific account (newest first).
     * The user must own the account — TransactionService enforces this.
     *
     * @param page  0-indexed page number (default 0)
     * @param size  page size (default 20)
     */
    @GetMapping("/account/{accountNumber}")
    @Operation(summary = "Get transaction history for an account")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> getTransactions(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(
                transactionService.getAccountTransactions(accountNumber, user.getId(), page, size)));
    }

    /**
     * Looks up a transaction by its public reference number (e.g. TXN1743921824569).
     * No ownership check — reference numbers are effectively unguessable (timestamp + random).
     * Used for receipt lookups and customer support queries.
     */
    @GetMapping("/ref/{transactionRef}")
    @Operation(summary = "Look up a transaction by reference number")
    public ResponseEntity<ApiResponse<TransactionResponse>> getByRef(@PathVariable String transactionRef) {
        return ResponseEntity.ok(ApiResponse.success(transactionService.getByRef(transactionRef)));
    }

    /**
     * Reverses a completed transaction (Admin only).
     * Applies the inverse balance change to the account and links the reversal to the original.
     * Only COMPLETED transactions can be reversed.
     */
    @PostMapping("/{transactionId}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reverse a completed transaction (Admin only)")
    public ResponseEntity<ApiResponse<TransactionResponse>> reverse(@PathVariable UUID transactionId) {
        User admin = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Transaction reversed",
                transactionService.reverseTransaction(transactionId, admin)));
    }
}
