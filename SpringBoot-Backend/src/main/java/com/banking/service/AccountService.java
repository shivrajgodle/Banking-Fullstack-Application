package com.banking.service;

import com.banking.dto.request.CreateAccountRequest;
import com.banking.dto.response.AccountResponse;
import com.banking.dto.response.DashboardResponse;
import com.banking.entity.Account;
import com.banking.entity.User;
import com.banking.enums.AccountStatus;
import com.banking.enums.AccountType;
import com.banking.enums.NotificationType;
import com.banking.exception.BankingException;
import com.banking.repository.AccountRepository;
import com.banking.repository.FixedDepositRepository;
import com.banking.repository.LoanRepository;
import com.banking.repository.TransactionRepository;
import com.banking.util.AccountNumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Manages the full lifecycle of bank accounts: creation, retrieval,
 * freeze/unfreeze, closure, and dashboard aggregation.
 *
 * Account rules enforced here:
 *  - A user may have at most 5 accounts total (across all types)
 *  - Account numbers are generated uniquely using a year-prefixed sequence
 *    with a DB uniqueness re-check loop (extremely unlikely to loop, but safe)
 *  - Savings accounts automatically receive the configured interest rate;
 *    Current/NRI accounts receive 0%
 *  - An account can only be closed if its balance is zero
 *  - Freeze/unfreeze is an admin/manager operation only (enforced at the controller level)
 *
 * The two package-visible helpers (getAccountOrThrow, getActiveAccountOrThrow)
 * are intentionally reused by TransactionService and LoanService to avoid
 * duplicating account lookup + status validation logic across services.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final LoanRepository loanRepository;
    private final FixedDepositRepository fdRepository;
    private final TransactionRepository transactionRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AuditService auditService;
    private final NotificationService notificationService;

    /** Annual interest rate for savings accounts. Defaults to 4% if not configured. */
    @Value("${banking.interest.savings-rate:0.04}")
    private BigDecimal savingsRate;

    /**
     * Opens a new bank account for the given user.
     *
     * Steps:
     *  1. Check the user hasn't exceeded the 5-account limit.
     *  2. Generate a unique account number (retry on collision — very rare).
     *  3. Set interest rate: savings → savingsRate, others → 0%.
     *  4. Persist the account with zero balance.
     *  5. Fire audit log and in-app notification (async).
     *
     * @throws BankingException 400 if 5 accounts already exist
     */
    @Transactional
    public AccountResponse createAccount(User user, CreateAccountRequest request) {
        // Cap at 5 accounts per user
        long existingCount = accountRepository.countByUserId(user.getId());
        if (existingCount >= 5) {
            throw BankingException.badRequest("Maximum number of accounts (5) reached");
        }

        // Generate a unique account number; loop is a safety net for the
        // extremely unlikely case of a sequence collision
        String accountNumber;
        do {
            accountNumber = accountNumberGenerator.generate();
        } while (accountRepository.existsByAccountNumber(accountNumber));

        // Only savings accounts earn interest
        BigDecimal interestRate = request.getAccountType() == AccountType.SAVINGS
                ? savingsRate : BigDecimal.ZERO;

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .user(user)
                .accountType(request.getAccountType())
                .status(AccountStatus.ACTIVE)
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .interestRate(interestRate)
                .nomineeName(request.getNomineeName())
                .nomineeRelation(request.getNomineeRelation())
                .openedDate(LocalDate.now())
                .build();

        account = accountRepository.save(account);

        // Async side-effects — failures here don't roll back the account creation
        auditService.log(user.getId(), "ACCOUNT_CREATED", "Account", account.getId().toString());
        notificationService.send(user,
                "Account Created",
                "Your " + request.getAccountType() + " account " + accountNumber + " has been created.",
                NotificationType.ACCOUNT, account.getId());

        log.info("Account {} created for user {}", accountNumber, user.getId());
        return toResponse(account);
    }

    /**
     * Returns all accounts belonging to a user, regardless of status.
     * Read-only transaction — skips dirty-checking for better performance.
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> getUserAccounts(UUID userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(this::toResponse).toList();
    }

    /**
     * Returns details for a single account, but only if the requesting user owns it.
     * Admins bypass this check at the controller level using a different endpoint.
     *
     * @throws BankingException 404 if account doesn't exist, 403 if not the owner
     */
    @Transactional(readOnly = true)
    public AccountResponse getAccountByNumber(String accountNumber, UUID userId) {
        Account account = getAccountOrThrow(accountNumber);
        if (!account.getUser().getId().equals(userId)) {
            throw BankingException.forbidden("Access denied to this account");
        }
        return toResponse(account);
    }

    /**
     * Freezes an account — no deposits or withdrawals are possible while frozen.
     * Only admins/managers can freeze accounts (enforced by @PreAuthorize at the controller).
     */
    @Transactional
    public AccountResponse freezeAccount(String accountNumber, UUID adminId) {
        Account account = getAccountOrThrow(accountNumber);
        account.setStatus(AccountStatus.FROZEN);
        account = accountRepository.save(account);
        auditService.log(adminId, "ACCOUNT_FROZEN", "Account", account.getId().toString());
        return toResponse(account);
    }

    /**
     * Restores a frozen account to ACTIVE status.
     * Only admins/managers can unfreeze (enforced by @PreAuthorize at the controller).
     */
    @Transactional
    public AccountResponse unfreezeAccount(String accountNumber, UUID adminId) {
        Account account = getAccountOrThrow(accountNumber);
        account.setStatus(AccountStatus.ACTIVE);
        account = accountRepository.save(account);
        auditService.log(adminId, "ACCOUNT_UNFROZEN", "Account", account.getId().toString());
        return toResponse(account);
    }

    /**
     * Permanently closes an account.
     *
     * Preconditions:
     *  - The requesting user must own the account.
     *  - The account balance must be exactly zero (customer must withdraw first).
     *
     * @throws BankingException 403 if not owner, 400 if balance is non-zero
     */
    @Transactional
    public AccountResponse closeAccount(String accountNumber, UUID userId) {
        Account account = getAccountOrThrow(accountNumber);
        if (!account.getUser().getId().equals(userId)) {
            throw BankingException.forbidden("Access denied");
        }
        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw BankingException.badRequest(
                    "Account has remaining balance. Please withdraw before closing.");
        }
        account.setStatus(AccountStatus.CLOSED);
        account.setClosedDate(LocalDate.now());
        account = accountRepository.save(account);
        auditService.log(userId, "ACCOUNT_CLOSED", "Account", account.getId().toString());
        return toResponse(account);
    }

    /**
     * Assembles a financial summary dashboard for the logged-in user.
     *
     * Aggregates:
     *  - Total balance across all ACTIVE accounts
     *  - Count and total outstanding amount of ACTIVE loans
     *  - Count and total principal of ACTIVE fixed deposits
     *  - 10 most recent transactions across all accounts (sorted by date)
     *  - Full list of all accounts
     *
     * This intentionally queries several tables in one call to reduce round-trips
     * for the dashboard landing page.
     */
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(User user) {
        List<Account> accounts = accountRepository.findByUserId(user.getId());

        // Sum balances only for active accounts (exclude frozen/closed)
        BigDecimal totalBalance = accounts.stream()
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Active loan count and outstanding principal
        long activeLoans = loanRepository.findByUserIdAndStatus(user.getId(),
                com.banking.enums.LoanStatus.ACTIVE).size();

        BigDecimal totalLoanOutstanding = loanRepository.findByUserIdAndStatus(user.getId(),
                com.banking.enums.LoanStatus.ACTIVE).stream()
                .map(l -> l.getOutstandingAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Active FD count and total principal (first 100 — enough for any real user)
        var activeFds = fdRepository.findByUserId(user.getId(),
                org.springframework.data.domain.PageRequest.of(0, 100));

        BigDecimal totalFdAmount = activeFds.getContent().stream()
                .filter(fd -> "ACTIVE".equals(fd.getStatus()))
                .map(fd -> fd.getPrincipalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Pull the 5 most recent transactions per account, flatten, re-sort, keep top 10
        var recentTxns = accounts.stream()
                .flatMap(a -> transactionRepository
                        .findByAccountIdOrderByCreatedAtDesc(a.getId(),
                                org.springframework.data.domain.PageRequest.of(0, 5))
                        .getContent().stream())
                .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt()))
                .limit(10)
                .map(t -> com.banking.dto.response.TransactionResponse.builder()
                        .id(t.getId())
                        .transactionRef(t.getTransactionRef())
                        .accountNumber(t.getAccount().getAccountNumber())
                        .transactionType(t.getTransactionType())
                        .amount(t.getAmount())
                        .status(t.getStatus())
                        .createdAt(t.getCreatedAt())
                        .build())
                .toList();

        return DashboardResponse.builder()
                .totalBalance(totalBalance)
                .totalAccounts(accounts.size())
                .activeLoans((int) activeLoans)
                .activeFds((int) activeFds.getContent().stream()
                        .filter(fd -> "ACTIVE".equals(fd.getStatus())).count())
                .totalLoanOutstanding(totalLoanOutstanding)
                .totalFdAmount(totalFdAmount)
                .recentTransactions(recentTxns)
                .accounts(accounts.stream().map(this::toResponse).toList())
                .build();
    }

    // ── Shared helpers used by TransactionService and LoanService ──────────────

    /**
     * Fetches an account by account number, throwing 404 if not found.
     * Used by any service that needs an account without status validation.
     */
    public Account getAccountOrThrow(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> BankingException.notFound("Account", accountNumber));
    }

    /**
     * Fetches an account and validates that it is ACTIVE (not frozen or closed).
     * Used by TransactionService and LoanService before debiting/crediting funds.
     *
     * @throws BankingException 403 ACCOUNT_FROZEN, or 400 if not active for any other reason
     */
    public Account getActiveAccountOrThrow(String accountNumber) {
        Account account = getAccountOrThrow(accountNumber);
        if (account.getStatus() == AccountStatus.FROZEN) throw BankingException.accountFrozen();
        if (account.getStatus() != AccountStatus.ACTIVE)
            throw BankingException.badRequest("Account is not active");
        return account;
    }

    /**
     * Maps an Account entity to the API response DTO.
     * Exposes all non-sensitive fields; does not expose internal entity IDs
     * beyond the account's own UUID.
     */
    public AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .ownerName(account.getUser().getFullName())
                .accountType(account.getAccountType())
                .status(account.getStatus())
                .balance(account.getBalance())
                .availableBalance(account.getAvailableBalance())
                .currency(account.getCurrency())
                .interestRate(account.getInterestRate())
                .dailyLimit(account.getDailyLimit())
                .nomineeName(account.getNomineeName())
                .nomineeRelation(account.getNomineeRelation())
                .branchCode(account.getBranchCode())
                .ifscCode(account.getIfscCode())
                .openedDate(account.getOpenedDate())
                .lastTransactionAt(account.getLastTransactionAt())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
