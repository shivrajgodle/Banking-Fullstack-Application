package com.banking.service;

import com.banking.dto.request.DepositRequest;
import com.banking.dto.request.TransferRequest;
import com.banking.dto.request.WithdrawalRequest;
import com.banking.dto.response.PageResponse;
import com.banking.dto.response.TransactionResponse;
import com.banking.entity.Account;
import com.banking.entity.Transaction;
import com.banking.entity.User;
import com.banking.enums.NotificationType;
import com.banking.enums.OtpType;
import com.banking.enums.TransactionStatus;
import com.banking.enums.TransactionType;
import com.banking.exception.BankingException;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionRepository;
import com.banking.util.AccountNumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Core financial transaction engine — deposits, withdrawals, fund transfers,
 * transaction history retrieval, and reversal.
 *
 * ─── Operation overview ───────────────────────────────────────────────────────
 *
 *  DEPOSIT   (Teller/Admin/Manager only)
 *    Cash or cheque credited to an account. No OTP required — a teller
 *    is present physically. Single transaction record created.
 *
 *  WITHDRAWAL (Any authenticated user)
 *    OTP required to prevent unauthorized cash-outs. Validates:
 *      - Account ownership
 *      - Minimum amount (≥ ₹1)
 *      - Single-transaction limit (configurable, default ₹2,00,000)
 *      - Daily outflow limit (configured per account, default ₹2,00,000)
 *      - Sufficient balance
 *
 *  TRANSFER (Any authenticated user)
 *    Moves money between two accounts. OTP required.
 *    Creates TWO transaction records:
 *      - TRANSFER_OUT on the source account (debit)
 *      - TRANSFER_IN  on the destination account (credit)
 *    Both share the same referenceNumber so they can be correlated.
 *    If the destination belongs to a different user, that user also receives
 *    an email + notification alert.
 *
 *  REVERSAL (Admin only)
 *    Undoes a completed transaction by applying the inverse balance change.
 *    Only COMPLETED transactions can be reversed.
 *    The original transaction is marked REVERSED; a new REVERSAL transaction is created.
 *
 * ─── Validation chain ─────────────────────────────────────────────────────────
 *  Every outflow (withdrawal/transfer) runs through the same four validators:
 *   validateAmount()       — positive, at least ₹1
 *   validateSingleLimit()  — doesn't exceed the per-transaction cap
 *   validateDailyLimit()   — today's total outflow + this amount ≤ account's daily limit
 *   validateBalance()      — available balance covers the amount
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final OtpService otpService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final AccountNumberGenerator accountNumberGenerator;

    /** Maximum amount allowed in a single transaction. Defaults to ₹2,00,000. */
    @Value("${banking.transaction.single-limit:200000}")
    private BigDecimal singleTransactionLimit;

    // ── Deposit ────────────────────────────────────────────────────────────────

    /**
     * Credits cash/cheque to an account (teller-initiated operation).
     * No OTP needed — the teller's presence and role are the authorization.
     *
     * Balance update:
     *   balanceAfter = balanceBefore + amount
     *
     * @param request    deposit details (account number, amount, payment mode)
     * @param performedBy the teller/admin performing the operation
     */
    @Transactional
    public TransactionResponse deposit(DepositRequest request, User performedBy) {
        Account account = accountService.getActiveAccountOrThrow(request.getAccountNumber());
        validateAmount(request.getAmount());

        BigDecimal balanceBefore = account.getBalance();
        BigDecimal balanceAfter  = balanceBefore.add(request.getAmount());

        // Update both balance fields — availableBalance mirrors balance for simple cases
        account.setBalance(balanceAfter);
        account.setAvailableBalance(balanceAfter);
        account.setLastTransactionAt(LocalDateTime.now());
        accountRepository.save(account);

        // Build and immediately complete the transaction record
        Transaction txn = buildTransaction(account, null, TransactionType.DEPOSIT,
                request.getAmount(), balanceBefore, balanceAfter,
                request.getDescription(), request.getPaymentMode(),
                request.getReferenceNumber(), performedBy);
        txn.setStatus(TransactionStatus.COMPLETED);
        txn.setCompletedAt(LocalDateTime.now());
        txn = transactionRepository.save(txn);

        // Fire async email + in-app notification to the account owner
        sendAlerts(account.getUser(), txn, "DEPOSIT");
        auditService.log(performedBy.getId(), "DEPOSIT", "Transaction", txn.getId().toString());

        log.info("Deposit {} of {} to account {}", txn.getTransactionRef(),
                request.getAmount(), account.getAccountNumber());
        return toResponse(txn);
    }

    // ── Withdrawal ─────────────────────────────────────────────────────────────

    /**
     * Debits the user's account after OTP verification.
     *
     * Security checks in order:
     *  1. OTP verification (must match an active TRANSACTION OTP for this user)
     *  2. Account ownership (user must own the account)
     *  3. Amount validations (amount, single limit, daily limit, balance)
     *
     * @throws BankingException 400 INVALID_OTP, 403 forbidden, 400 limit/balance errors
     */
    @Transactional
    public TransactionResponse withdraw(WithdrawalRequest request, User user) {
        // Step 1: OTP must be verified before touching any money
        otpService.verify(user.getId(), OtpType.TRANSACTION, request.getOtp());

        Account account = accountService.getActiveAccountOrThrow(request.getAccountNumber());

        // Step 2: Only the account owner can withdraw from it
        if (!account.getUser().getId().equals(user.getId())) {
            throw BankingException.forbidden("Access denied to this account");
        }

        // Step 3: Amount validation chain
        validateAmount(request.getAmount());
        validateSingleLimit(request.getAmount());
        validateDailyLimit(account, request.getAmount());
        validateBalance(account, request.getAmount());

        BigDecimal balanceBefore = account.getBalance();
        BigDecimal balanceAfter  = balanceBefore.subtract(request.getAmount());

        account.setBalance(balanceAfter);
        account.setAvailableBalance(balanceAfter);
        account.setLastTransactionAt(LocalDateTime.now());
        accountRepository.save(account);

        Transaction txn = buildTransaction(account, null, TransactionType.WITHDRAWAL,
                request.getAmount(), balanceBefore, balanceAfter,
                request.getDescription(), request.getPaymentMode(), null, user);
        txn.setStatus(TransactionStatus.COMPLETED);
        txn.setCompletedAt(LocalDateTime.now());
        txn = transactionRepository.save(txn);

        sendAlerts(user, txn, "WITHDRAWAL");
        auditService.log(user.getId(), "WITHDRAWAL", "Transaction", txn.getId().toString());

        return toResponse(txn);
    }

    // ── Fund Transfer ──────────────────────────────────────────────────────────

    /**
     * Transfers funds between two accounts (own-to-own or to another user's account).
     *
     * Atomicity: both debit and credit happen within the same @Transactional boundary.
     * If anything fails, both are rolled back — no money is lost or created.
     *
     * Two transaction records are created sharing the same txnRef:
     *   - TRANSFER_OUT on fromAccount
     *   - TRANSFER_IN  on toAccount
     * This allows reconciliation on both sides.
     *
     * @throws BankingException 400 if same account, 400 INVALID_OTP, 403 forbidden,
     *                          or any limit/balance violation
     */
    @Transactional
    public TransactionResponse transfer(TransferRequest request, User user) {
        // Self-transfer guard
        if (request.getFromAccountNumber().equals(request.getToAccountNumber())) {
            throw BankingException.badRequest("Source and destination accounts cannot be the same");
        }

        // OTP required for fund transfers
        otpService.verify(user.getId(), OtpType.TRANSACTION, request.getOtp());

        Account fromAccount = accountService.getActiveAccountOrThrow(request.getFromAccountNumber());
        Account toAccount   = accountService.getActiveAccountOrThrow(request.getToAccountNumber());

        // Only the owner of the source account can initiate a transfer
        if (!fromAccount.getUser().getId().equals(user.getId())) {
            throw BankingException.forbidden("Access denied to source account");
        }

        // Full validation chain on the debit side
        validateAmount(request.getAmount());
        validateSingleLimit(request.getAmount());
        validateDailyLimit(fromAccount, request.getAmount());
        validateBalance(fromAccount, request.getAmount());

        // Debit the source account
        BigDecimal fromBefore = fromAccount.getBalance();
        BigDecimal fromAfter  = fromBefore.subtract(request.getAmount());
        fromAccount.setBalance(fromAfter);
        fromAccount.setAvailableBalance(fromAfter);
        fromAccount.setLastTransactionAt(LocalDateTime.now());
        accountRepository.save(fromAccount);

        // Credit the destination account
        BigDecimal toBefore = toAccount.getBalance();
        BigDecimal toAfter  = toBefore.add(request.getAmount());
        toAccount.setBalance(toAfter);
        toAccount.setAvailableBalance(toAfter);
        toAccount.setLastTransactionAt(LocalDateTime.now());
        accountRepository.save(toAccount);

        // Shared reference number links the two transaction records
        String txnRef = accountNumberGenerator.generateTransactionRef();

        // Debit record
        Transaction debitTxn = buildTransaction(fromAccount, toAccount, TransactionType.TRANSFER_OUT,
                request.getAmount(), fromBefore, fromAfter,
                request.getDescription(), request.getPaymentMode(), txnRef, user);
        debitTxn.setStatus(TransactionStatus.COMPLETED);
        debitTxn.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(debitTxn);

        // Credit record
        Transaction creditTxn = buildTransaction(toAccount, fromAccount, TransactionType.TRANSFER_IN,
                request.getAmount(), toBefore, toAfter,
                request.getDescription(), request.getPaymentMode(), txnRef, user);
        creditTxn.setStatus(TransactionStatus.COMPLETED);
        creditTxn.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(creditTxn);

        // Alert the sender
        sendAlerts(user, debitTxn, "TRANSFER (DEBIT)");

        // Alert the recipient only if they're a different user
        if (!toAccount.getUser().getId().equals(user.getId())) {
            sendAlerts(toAccount.getUser(), creditTxn, "TRANSFER (CREDIT)");
        }

        auditService.log(user.getId(), "TRANSFER", "Transaction", debitTxn.getId().toString());

        // Return the debit side — the sender's perspective
        return toResponse(debitTxn);
    }

    // ── Reversal ───────────────────────────────────────────────────────────────

    /**
     * Reverses a completed transaction by applying the inverse balance change.
     * Admin-only operation (enforced by @PreAuthorize at the controller).
     *
     * Reversal logic:
     *  - DEPOSIT / TRANSFER_IN  → the account received money, so subtract it back
     *  - WITHDRAWAL / TRANSFER_OUT / LOAN_* → the account lost money, so add it back
     *
     * After reversal:
     *  - Original transaction status → REVERSED
     *  - New REVERSAL transaction created, linked via reversalOf field
     *
     * @throws BankingException 404 if transaction not found,
     *                          400 if it's not in COMPLETED status
     */
    @Transactional
    public TransactionResponse reverseTransaction(UUID transactionId, User admin) {
        Transaction original = transactionRepository.findById(transactionId)
                .orElseThrow(() -> BankingException.notFound("Transaction", transactionId.toString()));

        if (original.getStatus() != TransactionStatus.COMPLETED) {
            throw BankingException.badRequest("Only completed transactions can be reversed");
        }

        Account account = original.getAccount();
        BigDecimal balanceBefore = account.getBalance();
        BigDecimal balanceAfter;

        // Determine reversal direction: undo a credit → debit, undo a debit → credit
        if (original.getTransactionType() == TransactionType.DEPOSIT ||
            original.getTransactionType() == TransactionType.TRANSFER_IN) {
            balanceAfter = balanceBefore.subtract(original.getAmount()); // undo credit
        } else {
            balanceAfter = balanceBefore.add(original.getAmount()); // undo debit
        }

        account.setBalance(balanceAfter);
        account.setAvailableBalance(balanceAfter);
        accountRepository.save(account);

        // Mark the original as reversed
        original.setStatus(TransactionStatus.REVERSED);
        transactionRepository.save(original);

        // Create the reversal record and link it to the original
        Transaction reversalTxn = buildTransaction(account, null, TransactionType.REVERSAL,
                original.getAmount(), balanceBefore, balanceAfter,
                "Reversal of " + original.getTransactionRef(), null,
                original.getTransactionRef(), admin);
        reversalTxn.setReversalOf(original.getId()); // cross-reference to original
        reversalTxn.setStatus(TransactionStatus.COMPLETED);
        reversalTxn.setCompletedAt(LocalDateTime.now());
        reversalTxn = transactionRepository.save(reversalTxn);

        auditService.log(admin.getId(), "TRANSACTION_REVERSED", "Transaction",
                original.getId().toString());
        return toResponse(reversalTxn);
    }

    // ── Transaction History ────────────────────────────────────────────────────

    /**
     * Returns paginated transaction history for an account, sorted newest-first.
     * Ownership check ensures customers can only see their own accounts.
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getAccountTransactions(
            String accountNumber, UUID userId, int page, int size) {
        Account account = accountService.getAccountOrThrow(accountNumber);
        if (!account.getUser().getId().equals(userId)) {
            throw BankingException.forbidden("Access denied");
        }

        Page<Transaction> txnPage = transactionRepository
                .findByAccountIdOrderByCreatedAtDesc(account.getId(),
                        PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return PageResponse.<TransactionResponse>builder()
                .content(txnPage.getContent().stream().map(this::toResponse).toList())
                .page(page).size(size)
                .totalElements(txnPage.getTotalElements())
                .totalPages(txnPage.getTotalPages())
                .first(txnPage.isFirst()).last(txnPage.isLast())
                .build();
    }

    /**
     * Looks up a single transaction by its public reference number (e.g. TXN17439218245).
     * Used for receipt lookup and customer support queries.
     */
    @Transactional(readOnly = true)
    public TransactionResponse getByRef(String ref) {
        return transactionRepository.findByTransactionRef(ref)
                .map(this::toResponse)
                .orElseThrow(() -> BankingException.notFound("Transaction", ref));
    }

    // ── Private Validation Helpers ─────────────────────────────────────────────

    /** Rejects amounts below ₹1 (prevents zero/negative transactions). */
    private void validateAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ONE) < 0) {
            throw BankingException.badRequest("Amount must be at least ₹1");
        }
    }

    /** Rejects amounts exceeding the configured single-transaction cap. */
    private void validateSingleLimit(BigDecimal amount) {
        if (amount.compareTo(singleTransactionLimit) > 0) {
            throw BankingException.badRequest(
                    "Amount exceeds single transaction limit of ₹" + singleTransactionLimit);
        }
    }

    /**
     * Checks that today's total outflow (existing + this transaction) doesn't
     * exceed the account's daily limit.
     * Queries sumDailyOutflow which sums all COMPLETED debit transactions since midnight.
     */
    private void validateDailyLimit(Account account, BigDecimal amount) {
        BigDecimal todayOutflow = transactionRepository.sumDailyOutflow(
                account.getId(), LocalDateTime.now().toLocalDate().atStartOfDay());
        if (todayOutflow.add(amount).compareTo(account.getDailyLimit()) > 0) {
            throw BankingException.dailyLimitExceeded();
        }
    }

    /** Rejects if the available balance is less than the requested amount. */
    private void validateBalance(Account account, BigDecimal amount) {
        if (account.getAvailableBalance().compareTo(amount) < 0) {
            throw BankingException.insufficientFunds();
        }
    }

    // ── Private Builder Helper ─────────────────────────────────────────────────

    /**
     * Creates a Transaction entity with all mandatory fields pre-populated.
     * Callers set status and completedAt after calling this, then save.
     *
     * @param account       the primary account (debit or credit side)
     * @param counterparty  the other account in a transfer, null for deposits/withdrawals
     * @param type          the transaction type (DEPOSIT, WITHDRAWAL, TRANSFER_OUT, etc.)
     */
    private Transaction buildTransaction(Account account, Account counterparty,
                                          TransactionType type, BigDecimal amount,
                                          BigDecimal before, BigDecimal after,
                                          String description, String paymentMode,
                                          String referenceNumber, User initiatedBy) {
        return Transaction.builder()
                .transactionRef(accountNumberGenerator.generateTransactionRef())
                .account(account)
                .counterpartyAccount(counterparty)
                .transactionType(type)
                .amount(amount)
                .currency(account.getCurrency())
                .balanceBefore(before)   // snapshot for audit trail
                .balanceAfter(after)     // snapshot for audit trail
                .description(description)
                .paymentMode(paymentMode)
                .referenceNumber(referenceNumber)
                .initiatedBy(initiatedBy) // who triggered the operation
                .channel("ONLINE")
                .build();
    }

    /**
     * Sends both an email and an in-app notification to the affected user.
     * Both calls are @Async, so they don't block the transaction thread.
     */
    private void sendAlerts(User user, Transaction txn, String type) {
        notificationService.send(user,
                "Transaction Alert",
                String.format("%s of ₹%.2f processed. Ref: %s",
                        type, txn.getAmount(), txn.getTransactionRef()),
                NotificationType.TRANSACTION, txn.getId());
        emailService.sendTransactionAlert(user.getEmail(), user.getFirstName(),
                txn.getTransactionRef(), txn.getAmount(), txn.getBalanceAfter(), type);
    }

    /**
     * Maps a Transaction entity to the API response DTO.
     * Includes balance snapshots for statement-style display.
     */
    public TransactionResponse toResponse(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .transactionRef(t.getTransactionRef())
                .accountNumber(t.getAccount().getAccountNumber())
                .counterpartyAccountNumber(t.getCounterpartyAccount() != null
                        ? t.getCounterpartyAccount().getAccountNumber() : null)
                .transactionType(t.getTransactionType())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .balanceBefore(t.getBalanceBefore())
                .balanceAfter(t.getBalanceAfter())
                .status(t.getStatus())
                .description(t.getDescription())
                .referenceNumber(t.getReferenceNumber())
                .paymentMode(t.getPaymentMode())
                .channel(t.getChannel())
                .failureReason(t.getFailureReason())
                .completedAt(t.getCompletedAt())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
