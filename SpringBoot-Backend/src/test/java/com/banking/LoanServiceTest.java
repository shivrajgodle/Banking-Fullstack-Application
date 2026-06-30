package com.banking;

import com.banking.dto.request.LoanApplicationRequest;
import com.banking.dto.response.LoanResponse;
import com.banking.entity.Account;
import com.banking.entity.Loan;
import com.banking.entity.User;
import com.banking.enums.*;
import com.banking.exception.BankingException;
import com.banking.repository.AccountRepository;
import com.banking.repository.LoanRepository;
import com.banking.service.*;
import com.banking.util.AccountNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoanService Unit Tests")
class LoanServiceTest {

    @Mock private LoanRepository loanRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private TransactionService transactionService;
    @Mock private OtpService otpService;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;
    @Mock private AccountService accountService;
    @Mock private AccountNumberGenerator accountNumberGenerator;

    @InjectMocks
    private LoanService loanService;

    private User customer;
    private Account account;
    private Loan pendingLoan;

    @BeforeEach
    void setUp() {
        customer = User.builder()
                .firstName("Alice").lastName("Sharma")
                .email("alice@example.com")
                .role(UserRole.CUSTOMER).status(UserStatus.ACTIVE)
                .build();

        account = Account.builder()
                .accountNumber("ACC2500000010")
                .user(customer)
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("200000.00"))
                .availableBalance(new BigDecimal("200000.00"))
                .build();

        pendingLoan = Loan.builder()
                .id(UUID.randomUUID())
                .loanNumber("LN25000001")
                .user(customer)
                .account(account)
                .loanType(LoanType.PERSONAL)
                .principalAmount(new BigDecimal("100000.00"))
                .outstandingAmount(new BigDecimal("100000.00"))
                .interestRate(new BigDecimal("0.1200"))
                .tenureMonths(12)
                .emiAmount(new BigDecimal("8884.88"))
                .totalInterest(new BigDecimal("6618.56"))
                .totalPayable(new BigDecimal("106618.56"))
                .paidAmount(BigDecimal.ZERO)
                .status(LoanStatus.PENDING)
                .build();
    }

    // ---- EMI Calculation Tests ----

    @Test
    @DisplayName("Should calculate EMI correctly for standard loan")
    void calculateEmi_StandardLoan() {
        // ₹1,00,000 @ 12% for 12 months → EMI ≈ ₹8,884.88
        BigDecimal emi = loanService.calculateEmi(
                new BigDecimal("100000"), new BigDecimal("0.12"), 12);

        assertThat(emi).isBetween(new BigDecimal("8800"), new BigDecimal("8950"));
    }

    @Test
    @DisplayName("Should calculate EMI correctly for home loan")
    void calculateEmi_HomeLoan() {
        // ₹50,00,000 @ 8.75% for 240 months
        BigDecimal emi = loanService.calculateEmi(
                new BigDecimal("5000000"), new BigDecimal("0.0875"), 240);

        assertThat(emi).isBetween(new BigDecimal("43000"), new BigDecimal("46000"));
    }

    @Test
    @DisplayName("Should calculate EMI as principal/tenure when rate is zero")
    void calculateEmi_ZeroInterestRate() {
        BigDecimal emi = loanService.calculateEmi(
                new BigDecimal("60000"), BigDecimal.ZERO, 12);

        assertThat(emi).isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    // ---- Loan Application Tests ----

    @Test
    @DisplayName("Should create loan application successfully")
    void applyForLoan_Success() {
        LoanApplicationRequest request = new LoanApplicationRequest();
        request.setLoanType(LoanType.PERSONAL);
        request.setPrincipalAmount(new BigDecimal("100000.00"));
        request.setTenureMonths(12);
        request.setAccountNumber("ACC2500000010");
        request.setPurpose("Personal expenses");

        when(accountService.getActiveAccountOrThrow("ACC2500000010")).thenReturn(account);
        when(accountNumberGenerator.generateLoanNumber()).thenReturn("LN25000001");
        when(loanRepository.findByLoanNumber("LN25000001")).thenReturn(Optional.empty());
        when(loanRepository.save(any())).thenReturn(pendingLoan);

        LoanResponse response = loanService.applyForLoan(request, customer);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(LoanStatus.PENDING);
        assertThat(response.getLoanNumber()).isEqualTo("LN25000001");
        verify(notificationService).send(any(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Should reject application from non-owner")
    void applyForLoan_NonOwner_ThrowsException() {
        User otherUser = User.builder().build();
        LoanApplicationRequest request = new LoanApplicationRequest();
        request.setLoanType(LoanType.PERSONAL);
        request.setPrincipalAmount(new BigDecimal("100000.00"));
        request.setTenureMonths(12);
        request.setAccountNumber("ACC2500000010");

        when(accountService.getActiveAccountOrThrow("ACC2500000010")).thenReturn(account);

        assertThatThrownBy(() -> loanService.applyForLoan(request, otherUser))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("Access denied");
    }

    // ---- Loan Approval Tests ----

    @Test
    @DisplayName("Should approve pending loan")
    void approveLoan_Success() {
        User manager = User.builder().role(UserRole.MANAGER).build();
        when(loanRepository.findById(pendingLoan.getId())).thenReturn(Optional.of(pendingLoan));
        when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoanResponse response = loanService.approveLoan(pendingLoan.getId(), manager);

        assertThat(response.getStatus()).isEqualTo(LoanStatus.APPROVED);
    }

    @Test
    @DisplayName("Should throw exception when approving non-pending loan")
    void approveLoan_NonPending_ThrowsException() {
        User manager = User.builder().role(UserRole.MANAGER).build();
        pendingLoan.setStatus(LoanStatus.ACTIVE);
        when(loanRepository.findById(pendingLoan.getId())).thenReturn(Optional.of(pendingLoan));

        assertThatThrownBy(() -> loanService.approveLoan(pendingLoan.getId(), manager))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("Should reject pending loan")
    void rejectLoan_Success() {
        User manager = User.builder().role(UserRole.MANAGER).build();
        when(loanRepository.findById(pendingLoan.getId())).thenReturn(Optional.of(pendingLoan));
        when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoanResponse response = loanService.rejectLoan(pendingLoan.getId(), manager);

        assertThat(response.getStatus()).isEqualTo(LoanStatus.REJECTED);
    }

    @Test
    @DisplayName("Should disburse approved loan and credit account")
    void disburseLoan_Success() {
        User manager = User.builder().role(UserRole.MANAGER).build();
        pendingLoan.setStatus(LoanStatus.APPROVED);
        pendingLoan.setEmiDay(5);

        when(loanRepository.findById(pendingLoan.getId())).thenReturn(Optional.of(pendingLoan));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(loanRepository.save(any())).thenAnswer(inv -> {
            Loan saved = inv.getArgument(0);
            assertThat(saved.getStatus()).isEqualTo(LoanStatus.ACTIVE);
            assertThat(saved.getDisbursedDate()).isNotNull();
            return saved;
        });

        LoanResponse response = loanService.disburseLoan(pendingLoan.getId(), manager);

        assertThat(response.getStatus()).isEqualTo(LoanStatus.ACTIVE);
        // Account balance should be credited
        verify(accountRepository).save(argThat(acc ->
                acc.getBalance().compareTo(new BigDecimal("300000.00")) == 0
        ));
    }
}
