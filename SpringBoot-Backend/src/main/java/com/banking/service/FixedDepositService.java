package com.banking.service;

import com.banking.dto.request.CreateFdRequest;
import com.banking.dto.response.FixedDepositResponse;
import com.banking.dto.response.PageResponse;
import com.banking.entity.Account;
import com.banking.entity.FixedDeposit;
import com.banking.entity.User;
import com.banking.enums.AccountStatus;
import com.banking.enums.NotificationType;
import com.banking.exception.BankingException;
import com.banking.repository.AccountRepository;
import com.banking.repository.FixedDepositRepository;
import com.banking.util.AccountNumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Manages Fixed Deposits (FDs): creation, premature withdrawal,
 * paginated retrieval, and automated maturity processing.
 *
 * ─── What is a Fixed Deposit? ─────────────────────────────────────────────────
 * A FD locks a lump sum for a fixed period at a guaranteed interest rate.
 * At maturity, the customer receives the principal + compound interest.
 * Withdrawing before maturity attracts a penalty (reduced effective rate).
 *
 * ─── Compound Interest Formula ────────────────────────────────────────────────
 * Uses quarterly compounding:
 *   A = P × (1 + r/n)^(n×t)
 * where:
 *   P = principal
 *   r = annual interest rate (decimal)
 *   n = 4 (compounding periods per year — quarterly)
 *   t = time in years = tenureMonths / 12
 *
 * ─── Premature Withdrawal ─────────────────────────────────────────────────────
 * The effective rate is reduced by prematurePenalty (default 1% per annum).
 * The payout is computed on the reduced rate for the time elapsed so far.
 *
 * ─── Auto-Maturity (Scheduled) ────────────────────────────────────────────────
 * At 1:30 AM daily, FDs whose maturity date is today or earlier are processed:
 *   - Maturity amount is credited to the linked account.
 *   - If auto-renew is ON, the FD restarts with the same terms (principal re-deducted).
 *   - If auto-renew is OFF, the FD is marked MATURED and a notification is sent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FixedDepositService {

    private final FixedDepositRepository fdRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final AccountNumberGenerator accountNumberGenerator;

    /** Default annual FD interest rate. Configurable via banking.interest.fixed-deposit-rate. */
    @Value("${banking.interest.fixed-deposit-rate:0.065}")
    private BigDecimal defaultFdRate;

    // ── Create FD ──────────────────────────────────────────────────────────────

    /**
     * Creates a new Fixed Deposit by locking funds from the user's savings account.
     *
     * Steps:
     *  1. Validate account ownership and active status.
     *  2. Check sufficient balance in the source account.
     *  3. Deduct the principal from the account (funds are "locked" in the FD).
     *  4. Calculate the maturity amount using compound interest (quarterly).
     *  5. Persist the FD and send a notification.
     *
     * @throws BankingException 403 if not account owner, 400 INSUFFICIENT_FUNDS
     */
    @Transactional
    public FixedDepositResponse createFd(CreateFdRequest request, User user) {
        Account account = accountService.getActiveAccountOrThrow(request.getAccountNumber());
        if (!account.getUser().getId().equals(user.getId())) {
            throw BankingException.forbidden("Access denied");
        }
        if (account.getAvailableBalance().compareTo(request.getPrincipalAmount()) < 0) {
            throw BankingException.insufficientFunds();
        }

        // Deduct principal from the savings account — money moves from liquid to locked
        BigDecimal newBalance = account.getBalance().subtract(request.getPrincipalAmount());
        account.setBalance(newBalance);
        account.setAvailableBalance(newBalance);
        accountRepository.save(account);

        // Compute how much the FD will return at maturity
        BigDecimal maturityAmount = calculateMaturityAmount(
                request.getPrincipalAmount(), defaultFdRate, request.getTenureMonths());

        // Generate unique FD number with DB collision check
        String fdNumber;
        do {
            fdNumber = accountNumberGenerator.generateFdNumber();
        } while (fdRepository.findByFdNumber(fdNumber).isPresent());

        FixedDeposit fd = FixedDeposit.builder()
                .fdNumber(fdNumber)
                .account(account)
                .user(user)
                .principalAmount(request.getPrincipalAmount())
                .interestRate(defaultFdRate)
                .tenureMonths(request.getTenureMonths())
                .maturityAmount(maturityAmount)
                .maturityDate(LocalDate.now().plusMonths(request.getTenureMonths()))
                .interestPayout(request.getInterestPayout())
                .autoRenew(request.isAutoRenew())
                .status("ACTIVE")
                .openedDate(LocalDate.now())
                .build();

        fd = fdRepository.save(fd);

        notificationService.send(user, "Fixed Deposit Created",
                "FD " + fdNumber + " of ₹" + request.getPrincipalAmount() +
                " created. Matures on " + fd.getMaturityDate(),
                NotificationType.ACCOUNT, fd.getId());

        auditService.log(user.getId(), "FD_CREATED", "FixedDeposit", fd.getId().toString());
        return toResponse(fd);
    }

    // ── Premature Withdrawal ───────────────────────────────────────────────────

    /**
     * Allows the user to close an ACTIVE FD before its maturity date with a penalty.
     *
     * Payout calculation:
     *  effectiveRate = defaultFdRate − prematurePenalty   (e.g. 6.5% − 1% = 5.5%)
     *  payout = compound interest on effectiveRate for monthsElapsed
     *
     * The payout amount is credited back to the linked account.
     * The FD is then marked CLOSED with today's date.
     *
     * @throws BankingException 403 if not owner, 400 if FD is not ACTIVE
     */
    @Transactional
    public FixedDepositResponse prematureWithdrawal(UUID fdId, User user) {
        FixedDeposit fd = getFdOrThrow(fdId);
        if (!fd.getUser().getId().equals(user.getId())) {
            throw BankingException.forbidden("Access denied");
        }
        if (!"ACTIVE".equals(fd.getStatus())) {
            throw BankingException.badRequest("FD is not active");
        }

        // Calculate how many complete months the FD has been held
        int monthsElapsed = (int) (LocalDate.now().toEpochDay() - fd.getOpenedDate().toEpochDay()) / 30;

        // Apply penalty to the effective rate
        BigDecimal effectiveRate = defaultFdRate.subtract(fd.getPrematurePenalty());
        BigDecimal payoutAmount  = calculateMaturityAmount(
                fd.getPrincipalAmount(), effectiveRate, monthsElapsed);

        // Credit payout to the account
        Account account = fd.getAccount();
        BigDecimal newBalance = account.getBalance().add(payoutAmount);
        account.setBalance(newBalance);
        account.setAvailableBalance(newBalance);
        accountRepository.save(account);

        fd.setStatus("CLOSED");
        fd.setClosedDate(LocalDate.now());
        fdRepository.save(fd);

        notificationService.send(user, "FD Closed (Premature)",
                "FD " + fd.getFdNumber() + " closed prematurely. ₹" + payoutAmount + " credited to account.",
                NotificationType.ACCOUNT, fd.getId());

        auditService.log(user.getId(), "FD_PREMATURE_CLOSED", "FixedDeposit", fd.getId().toString());
        return toResponse(fd);
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    /** Returns paginated FDs for a user, sorted newest first. */
    @Transactional(readOnly = true)
    public PageResponse<FixedDepositResponse> getUserFds(UUID userId, int page, int size) {
        Page<FixedDeposit> fdPage = fdRepository.findByUserId(userId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return PageResponse.<FixedDepositResponse>builder()
                .content(fdPage.getContent().stream().map(this::toResponse).toList())
                .page(page).size(size)
                .totalElements(fdPage.getTotalElements())
                .totalPages(fdPage.getTotalPages())
                .first(fdPage.isFirst()).last(fdPage.isLast())
                .build();
    }

    // ── Scheduled Maturity Processing ──────────────────────────────────────────

    /**
     * Runs at 1:30 AM every day to process FDs that have reached their maturity date.
     *
     * For each matured FD:
     *  - Credit the maturity amount to the account.
     *  - If autoRenew = true:
     *      Deduct the principal again (re-locking it), reset the dates,
     *      recalculate the next maturity amount, and keep status ACTIVE.
     *  - If autoRenew = false:
     *      Mark as MATURED, set closed date, send notification.
     *
     * Runs slightly after the overdue loan job (1:00 AM) to avoid DB contention.
     */
    @Scheduled(cron = "0 30 1 * * *") // every day at 1:30 AM
    @Transactional
    public void processMatureFds() {
        fdRepository.findMaturingFDs(LocalDate.now()).forEach(fd -> {
            Account account  = fd.getAccount();
            // Credit the full maturity amount (principal + interest)
            BigDecimal newBalance = account.getBalance().add(fd.getMaturityAmount());
            account.setBalance(newBalance);
            account.setAvailableBalance(newBalance);
            accountRepository.save(account);

            if (fd.isAutoRenew()) {
                // Re-lock the principal for another identical tenure
                fd.setOpenedDate(LocalDate.now());
                fd.setMaturityDate(LocalDate.now().plusMonths(fd.getTenureMonths()));
                fd.setMaturityAmount(calculateMaturityAmount(
                        fd.getPrincipalAmount(), fd.getInterestRate(), fd.getTenureMonths()));

                // Deduct principal again from the freshly credited balance
                BigDecimal renewBalance = account.getBalance().subtract(fd.getPrincipalAmount());
                account.setBalance(renewBalance);
                account.setAvailableBalance(renewBalance);
                accountRepository.save(account);
                fdRepository.save(fd);
                log.info("FD {} auto-renewed", fd.getFdNumber());
            } else {
                fd.setStatus("MATURED");
                fd.setClosedDate(LocalDate.now());
                fdRepository.save(fd);
                notificationService.send(fd.getUser(), "FD Matured",
                        "FD " + fd.getFdNumber() + " matured. ₹" + fd.getMaturityAmount() + " credited.",
                        NotificationType.ACCOUNT, fd.getId());
                log.info("FD {} matured and credited", fd.getFdNumber());
            }
        });
    }

    // ── Compound Interest Calculation ──────────────────────────────────────────

    /**
     * Calculates the FD maturity amount using quarterly compound interest:
     *
     *   A = P × (1 + r/n)^(n × t)
     *
     * where:
     *   P = principal
     *   r = annual rate (decimal, e.g. 0.065)
     *   n = 4 (quarterly compounding)
     *   t = time in years = months / 12
     *
     * Note: uses Java double internally for the pow() call, then converts back
     * to BigDecimal. For very large principal values, a pure BigDecimal
     * implementation would be more precise, but double is sufficient here.
     *
     * @return maturity amount rounded to 2 decimal places
     */
    public BigDecimal calculateMaturityAmount(BigDecimal principal, BigDecimal annualRate, int months) {
        int    n      = 4;              // quarterly compounding
        double t      = months / 12.0;  // time in years
        double r      = annualRate.doubleValue();
        double amount = principal.doubleValue() * Math.pow(1 + r / n, n * t);
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }

    /** Fetches an FD by ID or throws 404. */
    private FixedDeposit getFdOrThrow(UUID fdId) {
        return fdRepository.findById(fdId)
                .orElseThrow(() -> BankingException.notFound("Fixed Deposit", fdId.toString()));
    }

    /** Maps a FixedDeposit entity to the API response DTO. */
    public FixedDepositResponse toResponse(FixedDeposit fd) {
        return FixedDepositResponse.builder()
                .id(fd.getId())
                .fdNumber(fd.getFdNumber())
                .accountNumber(fd.getAccount().getAccountNumber())
                .principalAmount(fd.getPrincipalAmount())
                .interestRate(fd.getInterestRate())
                .tenureMonths(fd.getTenureMonths())
                .maturityAmount(fd.getMaturityAmount())
                .maturityDate(fd.getMaturityDate())
                .interestPayout(fd.getInterestPayout())
                .autoRenew(fd.isAutoRenew())
                .status(fd.getStatus())
                .openedDate(fd.getOpenedDate())
                .createdAt(fd.getCreatedAt())
                .build();
    }
}
