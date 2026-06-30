package com.banking.service;

import com.banking.dto.request.LoanApplicationRequest;
import com.banking.dto.request.LoanRepaymentRequest;
import com.banking.dto.response.LoanResponse;
import com.banking.dto.response.PageResponse;
import com.banking.entity.Account;
import com.banking.entity.Loan;
import com.banking.entity.User;
import com.banking.enums.*;
import com.banking.exception.BankingException;
import com.banking.repository.AccountRepository;
import com.banking.repository.LoanRepository;
import com.banking.repository.TransactionRepository;
import com.banking.util.AccountNumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the complete loan lifecycle: application → approval → disbursement → repayment → closure.
 *
 * ─── Loan lifecycle states ────────────────────────────────────────────────────
 *  PENDING   → APPROVED  (by Manager/Admin via approveLoan)
 *  PENDING   → REJECTED  (by Manager/Admin via rejectLoan)
 *  APPROVED  → ACTIVE    (by Manager/Admin via disburseLoan — funds credited to account)
 *  ACTIVE    → OVERDUE   (automatic, by the @Scheduled cron job at 1 AM daily)
 *  ACTIVE    → CLOSED    (when outstanding amount reaches zero via repayLoan)
 *  OVERDUE   → ACTIVE    (when the user makes a repayment that clears the overdue amount)
 *
 * ─── EMI Calculation ──────────────────────────────────────────────────────────
 * Uses the Reducing Balance (flat annuity) formula:
 *   EMI = P × r × (1+r)^n / ((1+r)^n − 1)
 * where:
 *   P = principal amount
 *   r = monthly interest rate = annual rate / 12
 *   n = tenure in months
 *
 * ─── Interest rates by loan type ──────────────────────────────────────────────
 *   PERSONAL  12%   BUSINESS  11%
 *   HOME      8.75% AUTO      9.5%
 *   EDUCATION  8%   GOLD      7.5%
 *
 * ─── Overdue penalty ──────────────────────────────────────────────────────────
 * Calculated daily as: EMI × penaltyRate × overdueDays
 * penaltyRate defaults to 2% per day on the overdue amount.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanService {

    private final LoanRepository loanRepository;
    private final AccountRepository accountRepository;
    private final TransactionService transactionService;
    private final OtpService otpService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final AccountService accountService;
    private final AccountNumberGenerator accountNumberGenerator;

    /**
     * Annual interest rates keyed by loan type.
     * Stored as BigDecimal to avoid floating-point precision issues in financial math.
     */
    private static final Map<LoanType, BigDecimal> LOAN_RATES = new HashMap<>() {{
        put(LoanType.PERSONAL,  new BigDecimal("0.1200"));
        put(LoanType.HOME,      new BigDecimal("0.0875"));
        put(LoanType.AUTO,      new BigDecimal("0.0950"));
        put(LoanType.EDUCATION, new BigDecimal("0.0800"));
        put(LoanType.BUSINESS,  new BigDecimal("0.1100"));
        put(LoanType.GOLD,      new BigDecimal("0.0750"));
    }};

    // ── Apply ──────────────────────────────────────────────────────────────────

    /**
     * Submits a new loan application.
     *
     * Steps:
     *  1. Validate the linked account belongs to the applicant.
     *  2. Determine the applicable interest rate from LOAN_RATES (default 12%).
     *  3. Calculate EMI, total interest, and total payable using the reducing balance formula.
     *  4. Generate a unique loan number and persist the loan in PENDING status.
     *  5. Send notification to the applicant.
     *
     * The loan is not active until approved + disbursed by a manager/admin.
     */
    @Transactional
    public LoanResponse applyForLoan(LoanApplicationRequest request, User user) {
        Account account = accountService.getActiveAccountOrThrow(request.getAccountNumber());
        if (!account.getUser().getId().equals(user.getId())) {
            throw BankingException.forbidden("Access denied to this account");
        }

        BigDecimal interestRate  = LOAN_RATES.getOrDefault(request.getLoanType(), new BigDecimal("0.12"));
        BigDecimal emi           = calculateEmi(request.getPrincipalAmount(), interestRate, request.getTenureMonths());
        BigDecimal totalPayable  = emi.multiply(BigDecimal.valueOf(request.getTenureMonths()));
        BigDecimal totalInterest = totalPayable.subtract(request.getPrincipalAmount());

        // Unique loan number with DB collision check
        String loanNumber;
        do {
            loanNumber = accountNumberGenerator.generateLoanNumber();
        } while (loanRepository.findByLoanNumber(loanNumber).isPresent());

        Loan loan = Loan.builder()
                .loanNumber(loanNumber)
                .user(user)
                .account(account)
                .loanType(request.getLoanType())
                .principalAmount(request.getPrincipalAmount())
                .outstandingAmount(request.getPrincipalAmount()) // starts at full principal
                .interestRate(interestRate)
                .tenureMonths(request.getTenureMonths())
                .emiAmount(emi)
                .totalInterest(totalInterest)
                .totalPayable(totalPayable)
                .paidAmount(BigDecimal.ZERO)
                .status(LoanStatus.PENDING)
                .purpose(request.getPurpose())
                .collateralType(request.getCollateralType())
                .collateralValue(request.getCollateralValue())
                .build();

        loan = loanRepository.save(loan);

        notificationService.send(user, "Loan Application Received",
                "Your loan application " + loanNumber + " for ₹" + request.getPrincipalAmount() +
                " has been received and is under review.", NotificationType.LOAN, loan.getId());

        auditService.log(user.getId(), "LOAN_APPLIED", "Loan", loan.getId().toString());
        return toResponse(loan);
    }

    // ── Approve ────────────────────────────────────────────────────────────────

    /**
     * Approves a PENDING loan (Manager/Admin only — enforced at controller level).
     * Moves status from PENDING → APPROVED. Disbursement is a separate step.
     */
    @Transactional
    public LoanResponse approveLoan(UUID loanId, User approver) {
        Loan loan = getLoanOrThrow(loanId);
        if (loan.getStatus() != LoanStatus.PENDING) {
            throw BankingException.badRequest("Loan is not in PENDING status");
        }

        loan.setStatus(LoanStatus.APPROVED);
        loan.setApprovedBy(approver);  // record who approved it for audit
        loan.setApprovedAt(LocalDateTime.now());
        loan = loanRepository.save(loan);

        notificationService.send(loan.getUser(), "Loan Approved!",
                "Your loan " + loan.getLoanNumber() + " has been approved. Disbursement in progress.",
                NotificationType.LOAN, loan.getId());

        auditService.log(approver.getId(), "LOAN_APPROVED", "Loan", loan.getId().toString());
        return toResponse(loan);
    }

    // ── Disburse ───────────────────────────────────────────────────────────────

    /**
     * Disburses an APPROVED loan — credits the principal to the linked account.
     *
     * Steps:
     *  1. Validate status is APPROVED (not already disbursed).
     *  2. Add the principal to the account balance directly (bypasses transaction limits
     *     as this is a bank-initiated credit, not a customer transfer).
     *  3. Set the first EMI date to one month after disbursement on emiDay (default 5th).
     *  4. Activate the loan.
     *
     * Note: Ideally this would create a LOAN_DISBURSEMENT transaction record too.
     * The current implementation updates the account balance directly for simplicity.
     */
    @Transactional
    public LoanResponse disburseLoan(UUID loanId, User approver) {
        Loan loan = getLoanOrThrow(loanId);
        if (loan.getStatus() != LoanStatus.APPROVED) {
            throw BankingException.badRequest("Loan must be APPROVED before disbursement");
        }

        // Credit the principal to the linked account
        Account account = loan.getAccount();
        BigDecimal balanceBefore = account.getBalance();
        BigDecimal balanceAfter  = balanceBefore.add(loan.getPrincipalAmount());
        account.setBalance(balanceAfter);
        account.setAvailableBalance(balanceAfter);
        account.setLastTransactionAt(LocalDateTime.now());
        accountRepository.save(account);

        LocalDate disbursedDate  = LocalDate.now();
        // First EMI is due one month from disbursement, on the configured emiDay
        LocalDate firstEmiDate   = disbursedDate.plusMonths(1).withDayOfMonth(loan.getEmiDay());

        loan.setStatus(LoanStatus.ACTIVE);
        loan.setDisbursedAmount(loan.getPrincipalAmount());
        loan.setDisbursedDate(disbursedDate);
        loan.setMaturityDate(disbursedDate.plusMonths(loan.getTenureMonths()));
        loan.setNextEmiDate(firstEmiDate);
        loan = loanRepository.save(loan);

        notificationService.send(loan.getUser(), "Loan Disbursed",
                "₹" + loan.getPrincipalAmount() + " has been credited to your account for loan "
                + loan.getLoanNumber(), NotificationType.LOAN, loan.getId());

        auditService.log(approver.getId(), "LOAN_DISBURSED", "Loan", loan.getId().toString());
        return toResponse(loan);
    }

    // ── Reject ─────────────────────────────────────────────────────────────────

    /**
     * Rejects a PENDING loan application.
     * Only PENDING loans can be rejected (not already approved/active ones).
     */
    @Transactional
    public LoanResponse rejectLoan(UUID loanId, User approver) {
        Loan loan = getLoanOrThrow(loanId);
        if (loan.getStatus() != LoanStatus.PENDING) {
            throw BankingException.badRequest("Only PENDING loans can be rejected");
        }
        loan.setStatus(LoanStatus.REJECTED);
        loan.setApprovedBy(approver);
        loan.setApprovedAt(LocalDateTime.now());
        loan = loanRepository.save(loan);

        notificationService.send(loan.getUser(), "Loan Application Rejected",
                "Your loan application " + loan.getLoanNumber() + " has been rejected.",
                NotificationType.LOAN, loan.getId());

        auditService.log(approver.getId(), "LOAN_REJECTED", "Loan", loan.getId().toString());
        return toResponse(loan);
    }

    // ── Repay ──────────────────────────────────────────────────────────────────

    /**
     * Processes a loan repayment, deducting the amount from the linked account.
     *
     * Repayment logic:
     *  - The actual repaid amount is capped at the outstanding balance
     *    (prevents overpayment if user enters more than owed).
     *  - When outstanding reaches zero, the loan is CLOSED.
     *  - If the loan was OVERDUE, it reverts to ACTIVE and overdue counters reset.
     *  - Otherwise, the next EMI date advances by one month.
     *
     * OTP required to prevent unauthorized repayments from the user's account.
     */
    @Transactional
    public LoanResponse repayLoan(LoanRepaymentRequest request, User user) {
        // OTP verification — TRANSACTION type covers repayments too
        otpService.verify(user.getId(), OtpType.TRANSACTION, request.getOtp());

        Loan loan = loanRepository.findByLoanNumber(request.getLoanNumber())
                .orElseThrow(() -> BankingException.notFound("Loan", request.getLoanNumber()));

        if (!loan.getUser().getId().equals(user.getId())) {
            throw BankingException.forbidden("Access denied");
        }
        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.OVERDUE) {
            throw BankingException.badRequest("Loan is not active");
        }

        Account account = accountService.getActiveAccountOrThrow(request.getAccountNumber());
        if (!account.getUser().getId().equals(user.getId())) {
            throw BankingException.forbidden("Access denied to this account");
        }
        if (account.getAvailableBalance().compareTo(request.getAmount()) < 0) {
            throw BankingException.insufficientFunds();
        }

        // Cap repayment at outstanding amount — can't overpay a loan
        BigDecimal repayAmount = request.getAmount().min(loan.getOutstandingAmount());

        // Debit the repayment from the customer's account
        BigDecimal accBefore = account.getBalance();
        BigDecimal accAfter  = accBefore.subtract(repayAmount);
        account.setBalance(accAfter);
        account.setAvailableBalance(accAfter);
        account.setLastTransactionAt(LocalDateTime.now());
        accountRepository.save(account);

        // Update loan records
        loan.setPaidAmount(loan.getPaidAmount().add(repayAmount));
        loan.setOutstandingAmount(loan.getOutstandingAmount().subtract(repayAmount));
        loan.setLastPaymentDate(LocalDate.now());

        if (loan.getOutstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            // Loan fully paid off
            loan.setStatus(LoanStatus.CLOSED);
            loan.setOutstandingAmount(BigDecimal.ZERO); // guard against rounding edge cases
            notificationService.send(user, "Loan Closed!",
                    "Congratulations! Your loan " + loan.getLoanNumber() + " has been fully repaid.",
                    NotificationType.LOAN, loan.getId());
        } else {
            // Advance EMI schedule to next month
            loan.setNextEmiDate(loan.getNextEmiDate().plusMonths(1));
            // If the loan was overdue, reset overdue tracking on successful payment
            if (loan.getStatus() == LoanStatus.OVERDUE) {
                loan.setStatus(LoanStatus.ACTIVE);
                loan.setOverdueDays(0);
                loan.setOverdueAmount(BigDecimal.ZERO);
            }
        }

        loan = loanRepository.save(loan);

        auditService.log(user.getId(), "LOAN_REPAYMENT", "Loan", loan.getId().toString());
        notificationService.send(user, "Loan Payment Received",
                "Payment of ₹" + repayAmount + " received for loan " + loan.getLoanNumber() +
                ". Outstanding: ₹" + loan.getOutstandingAmount(),
                NotificationType.LOAN, loan.getId());

        return toResponse(loan);
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    /** Returns paginated list of all loans for a user, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<LoanResponse> getUserLoans(UUID userId, int page, int size) {
        Page<Loan> loanPage = loanRepository.findByUserId(userId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return PageResponse.<LoanResponse>builder()
                .content(loanPage.getContent().stream().map(this::toResponse).toList())
                .page(page).size(size)
                .totalElements(loanPage.getTotalElements())
                .totalPages(loanPage.getTotalPages())
                .first(loanPage.isFirst()).last(loanPage.isLast())
                .build();
    }

    /** Returns a single loan, verifying ownership. */
    @Transactional(readOnly = true)
    public LoanResponse getLoan(UUID loanId, UUID userId) {
        Loan loan = getLoanOrThrow(loanId);
        if (!loan.getUser().getId().equals(userId)) throw BankingException.forbidden("Access denied");
        return toResponse(loan);
    }

    // ── Scheduled Overdue Processing ───────────────────────────────────────────

    /**
     * Runs at 1:00 AM every day to mark active loans with a past-due EMI date as OVERDUE
     * and calculate the applicable penalty.
     *
     * LoanRepository.findOverdueLoans() returns ACTIVE loans whose nextEmiDate < today.
     *
     * Penalty formula: EMI × penaltyRate × overdueDays
     * penaltyRate is stored per loan (default 2% per day).
     *
     * This runs in a @Transactional context; all updates are committed atomically.
     */
    @Scheduled(cron = "0 0 1 * * *") // every day at 1:00 AM server time
    @Transactional
    public void processOverdueLoans() {
        loanRepository.findOverdueLoans(LocalDate.now()).forEach(loan -> {
            loan.setStatus(LoanStatus.OVERDUE);
            // How many days has the EMI been missed?
            int overdueDays = (int) (LocalDate.now().toEpochDay() - loan.getNextEmiDate().toEpochDay());
            loan.setOverdueDays(overdueDays);
            // Penalty = EMI amount × penalty rate × overdue days
            BigDecimal penalty = loan.getEmiAmount()
                    .multiply(loan.getPenaltyRate())
                    .multiply(BigDecimal.valueOf(overdueDays));
            loan.setOverdueAmount(penalty);
            loanRepository.save(loan);
            log.info("Loan {} marked overdue ({} days)", loan.getLoanNumber(), overdueDays);
        });
    }

    // ── EMI Formula ────────────────────────────────────────────────────────────

    /**
     * Calculates the monthly EMI using the standard Reducing Balance (annuity) formula:
     *
     *   EMI = P × r × (1+r)^n / ((1+r)^n − 1)
     *
     * where:
     *   P  = principal
     *   r  = monthly interest rate  = annualRate / 12
     *   n  = tenure in months
     *
     * Edge case: if r = 0 (0% interest), falls back to simple division: EMI = P / n
     *
     * Uses MathContext(10) for precision during intermediate calculations,
     * then rounds to 2 decimal places (paise) for the final EMI value.
     *
     * @param principal     loan amount in rupees
     * @param annualRate    annual interest rate as a decimal (e.g. 0.12 for 12%)
     * @param tenureMonths  repayment period in months
     * @return monthly EMI rounded to 2 decimal places
     */
    public BigDecimal calculateEmi(BigDecimal principal, BigDecimal annualRate, int tenureMonths) {
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        // Special case: 0% interest loan → simple equal-part division
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(tenureMonths), 2, RoundingMode.HALF_UP);
        }

        BigDecimal onePlusR  = BigDecimal.ONE.add(monthlyRate);
        BigDecimal pow       = onePlusR.pow(tenureMonths, new MathContext(10, RoundingMode.HALF_UP)); // (1+r)^n
        BigDecimal numerator = principal.multiply(monthlyRate).multiply(pow);                         // P × r × (1+r)^n
        BigDecimal denominator = pow.subtract(BigDecimal.ONE);                                         // (1+r)^n - 1
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }

    /** Fetches a loan by ID or throws 404. */
    private Loan getLoanOrThrow(UUID loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> BankingException.notFound("Loan", loanId.toString()));
    }

    /** Maps a Loan entity to the API response DTO. */
    public LoanResponse toResponse(Loan loan) {
        return LoanResponse.builder()
                .id(loan.getId())
                .loanNumber(loan.getLoanNumber())
                .borrowerName(loan.getUser().getFullName())
                .loanType(loan.getLoanType())
                .principalAmount(loan.getPrincipalAmount())
                .outstandingAmount(loan.getOutstandingAmount())
                .interestRate(loan.getInterestRate())
                .tenureMonths(loan.getTenureMonths())
                .emiAmount(loan.getEmiAmount())
                .emiDay(loan.getEmiDay())
                .totalInterest(loan.getTotalInterest())
                .totalPayable(loan.getTotalPayable())
                .paidAmount(loan.getPaidAmount())
                .status(loan.getStatus())
                .purpose(loan.getPurpose())
                .disbursedDate(loan.getDisbursedDate())
                .maturityDate(loan.getMaturityDate())
                .nextEmiDate(loan.getNextEmiDate())
                .overdueAmount(loan.getOverdueAmount())
                .overdueDays(loan.getOverdueDays())
                .createdAt(loan.getCreatedAt())
                .build();
    }
}
