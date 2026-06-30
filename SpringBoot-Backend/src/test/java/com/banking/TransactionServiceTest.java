package com.banking;

import com.banking.dto.request.DepositRequest;
import com.banking.dto.request.TransferRequest;
import com.banking.dto.request.WithdrawalRequest;
import com.banking.dto.response.TransactionResponse;
import com.banking.entity.Account;
import com.banking.entity.Transaction;
import com.banking.entity.User;
import com.banking.enums.*;
import com.banking.exception.BankingException;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionRepository;
import com.banking.service.*;
import com.banking.util.AccountNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Unit Tests")
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountService accountService;
    @Mock private OtpService otpService;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;
    @Mock private AccountNumberGenerator accountNumberGenerator;

    @InjectMocks
    private TransactionService transactionService;

    private User customer;
    private Account account;
    private Account targetAccount;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(transactionService, "singleTransactionLimit", new BigDecimal("200000"));

        customer = User.builder()
                .firstName("Jane").lastName("Smith")
                .email("jane@example.com")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        account = Account.builder()
                .accountNumber("ACC2500000001")
                .user(customer)
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("50000.00"))
                .availableBalance(new BigDecimal("50000.00"))
                .currency("INR")
                .dailyLimit(new BigDecimal("200000"))
                .build();

        targetAccount = Account.builder()
                .accountNumber("ACC2500000002")
                .user(User.builder().firstName("Bob").lastName("Jones")
                        .email("bob@example.com").build())
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("10000.00"))
                .availableBalance(new BigDecimal("10000.00"))
                .currency("INR")
                .dailyLimit(new BigDecimal("200000"))
                .build();

        Transaction savedTxn = Transaction.builder()
                .id(UUID.randomUUID())
                .transactionRef("TXN1234567890")
                .account(account)
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .balanceBefore(new BigDecimal("50000.00"))
                .balanceAfter(new BigDecimal("55000.00"))
                .status(TransactionStatus.COMPLETED)
                .completedAt(LocalDateTime.now())
                .build();

        when(accountNumberGenerator.generateTransactionRef()).thenReturn("TXN1234567890");
        when(transactionRepository.save(any())).thenReturn(savedTxn);
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- Deposit Tests ----

    @Test
    @DisplayName("Should deposit successfully and update balance")
    void deposit_Success() {
        DepositRequest request = new DepositRequest();
        request.setAccountNumber("ACC2500000001");
        request.setAmount(new BigDecimal("5000.00"));
        request.setDescription("Cash deposit");
        request.setPaymentMode("CASH");

        User teller = User.builder().role(UserRole.TELLER).build();
        when(accountService.getActiveAccountOrThrow("ACC2500000001")).thenReturn(account);

        TransactionResponse response = transactionService.deposit(request, teller);

        assertThat(response).isNotNull();
        assertThat(response.getTransactionRef()).isEqualTo("TXN1234567890");
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("Should reject deposit with amount below minimum")
    void deposit_BelowMinAmount_ThrowsException() {
        DepositRequest request = new DepositRequest();
        request.setAccountNumber("ACC2500000001");
        request.setAmount(new BigDecimal("0.50"));
        request.setPaymentMode("CASH");

        when(accountService.getActiveAccountOrThrow(any())).thenReturn(account);

        assertThatThrownBy(() -> transactionService.deposit(request, customer))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("at least");
    }

    // ---- Withdrawal Tests ----

    @Test
    @DisplayName("Should withdraw successfully when balance is sufficient")
    void withdraw_Success() {
        WithdrawalRequest request = new WithdrawalRequest();
        request.setAccountNumber("ACC2500000001");
        request.setAmount(new BigDecimal("1000.00"));
        request.setOtp("123456");
        request.setPaymentMode("CASH");

        doNothing().when(otpService).verify(any(), any(), any());
        when(accountService.getActiveAccountOrThrow("ACC2500000001")).thenReturn(account);
        when(transactionRepository.sumDailyOutflow(any(), any())).thenReturn(BigDecimal.ZERO);

        Transaction withdrawTxn = Transaction.builder()
                .id(UUID.randomUUID())
                .transactionRef("TXN1234567890")
                .account(account)
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("1000.00"))
                .balanceBefore(new BigDecimal("50000.00"))
                .balanceAfter(new BigDecimal("49000.00"))
                .status(TransactionStatus.COMPLETED)
                .build();
        when(transactionRepository.save(any())).thenReturn(withdrawTxn);

        TransactionResponse response = transactionService.withdraw(request, customer);

        assertThat(response).isNotNull();
        verify(otpService).verify(any(), eq(OtpType.TRANSACTION), eq("123456"));
    }

    @Test
    @DisplayName("Should throw INSUFFICIENT_FUNDS on overdraft attempt")
    void withdraw_InsufficientFunds_ThrowsException() {
        WithdrawalRequest request = new WithdrawalRequest();
        request.setAccountNumber("ACC2500000001");
        request.setAmount(new BigDecimal("100000.00")); // More than balance
        request.setOtp("123456");

        doNothing().when(otpService).verify(any(), any(), any());
        when(accountService.getActiveAccountOrThrow(any())).thenReturn(account);
        when(transactionRepository.sumDailyOutflow(any(), any())).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> transactionService.withdraw(request, customer))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("Insufficient");
    }

    @Test
    @DisplayName("Should throw exception for unauthorized withdrawal from another account")
    void withdraw_UnauthorizedAccount_ThrowsException() {
        User otherUser = User.builder().build();
        WithdrawalRequest request = new WithdrawalRequest();
        request.setAccountNumber("ACC2500000001");
        request.setAmount(new BigDecimal("100.00"));
        request.setOtp("123456");

        doNothing().when(otpService).verify(any(), any(), any());
        when(accountService.getActiveAccountOrThrow(any())).thenReturn(account);

        // otherUser is not the account owner
        assertThatThrownBy(() -> transactionService.withdraw(request, otherUser))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("Access denied");
    }

    // ---- Transfer Tests ----

    @Test
    @DisplayName("Should transfer funds successfully between accounts")
    void transfer_Success() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountNumber("ACC2500000001");
        request.setToAccountNumber("ACC2500000002");
        request.setAmount(new BigDecimal("5000.00"));
        request.setOtp("123456");
        request.setPaymentMode("IMPS");

        doNothing().when(otpService).verify(any(), any(), any());
        when(accountService.getActiveAccountOrThrow("ACC2500000001")).thenReturn(account);
        when(accountService.getActiveAccountOrThrow("ACC2500000002")).thenReturn(targetAccount);
        when(transactionRepository.sumDailyOutflow(any(), any())).thenReturn(BigDecimal.ZERO);

        TransactionResponse response = transactionService.transfer(request, customer);

        assertThat(response).isNotNull();
        // Both accounts should be saved (debit + credit)
        verify(accountRepository, times(2)).save(any(Account.class));
        // Both debit and credit transactions should be saved
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should reject transfer when source and dest are the same account")
    void transfer_SameAccount_ThrowsException() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountNumber("ACC2500000001");
        request.setToAccountNumber("ACC2500000001");
        request.setAmount(new BigDecimal("1000.00"));
        request.setOtp("123456");

        assertThatThrownBy(() -> transactionService.transfer(request, customer))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("same");
    }

    @Test
    @DisplayName("Should reject transfer exceeding single transaction limit")
    void transfer_ExceedsSingleLimit_ThrowsException() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountNumber("ACC2500000001");
        request.setToAccountNumber("ACC2500000002");
        request.setAmount(new BigDecimal("300000.00")); // Exceeds 200k limit
        request.setOtp("123456");

        doNothing().when(otpService).verify(any(), any(), any());
        when(accountService.getActiveAccountOrThrow("ACC2500000001")).thenReturn(account);
        when(accountService.getActiveAccountOrThrow("ACC2500000002")).thenReturn(targetAccount);

        assertThatThrownBy(() -> transactionService.transfer(request, customer))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("single transaction limit");
    }
}
