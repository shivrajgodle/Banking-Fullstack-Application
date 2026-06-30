package com.banking;

import com.banking.dto.request.CreateAccountRequest;
import com.banking.dto.response.AccountResponse;
import com.banking.entity.Account;
import com.banking.entity.User;
import com.banking.enums.*;
import com.banking.exception.BankingException;
import com.banking.repository.AccountRepository;
import com.banking.repository.FixedDepositRepository;
import com.banking.repository.LoanRepository;
import com.banking.repository.TransactionRepository;
import com.banking.service.AccountService;
import com.banking.service.AuditService;
import com.banking.service.NotificationService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private FixedDepositRepository fdRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountNumberGenerator accountNumberGenerator;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private AccountService accountService;

    private User customer;
    private Account savingsAccount;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(accountService, "savingsRate", new BigDecimal("0.04"));

        customer = User.builder()
                .firstName("Ravi").lastName("Kumar")
                .email("ravi@example.com")
                .role(UserRole.CUSTOMER).status(UserStatus.ACTIVE)
                .build();

        savingsAccount = Account.builder()
                .id(UUID.randomUUID())
                .accountNumber("ACC2500100001")
                .user(customer)
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("25000.00"))
                .availableBalance(new BigDecimal("25000.00"))
                .currency("INR")
                .build();
    }

    @Test
    @DisplayName("Should create savings account with correct interest rate")
    void createAccount_Savings_Success() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountType(AccountType.SAVINGS);
        request.setCurrency("INR");

        when(accountRepository.countByUserId(customer.getId())).thenReturn(0L);
        when(accountNumberGenerator.generate()).thenReturn("ACC2500100002");
        when(accountRepository.existsByAccountNumber("ACC2500100002")).thenReturn(false);
        when(accountRepository.save(any())).thenReturn(savingsAccount);

        AccountResponse response = accountService.createAccount(customer, request);

        assertThat(response).isNotNull();
        verify(accountRepository).save(argThat(acc ->
                acc.getInterestRate().compareTo(new BigDecimal("0.04")) == 0 &&
                acc.getAccountType() == AccountType.SAVINGS
        ));
    }

    @Test
    @DisplayName("Should create current account with zero interest rate")
    void createAccount_Current_ZeroInterest() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountType(AccountType.CURRENT);
        request.setCurrency("INR");

        Account currentAccount = Account.builder()
                .accountNumber("ACC2500100003")
                .user(customer).accountType(AccountType.CURRENT)
                .status(AccountStatus.ACTIVE)
                .balance(BigDecimal.ZERO).availableBalance(BigDecimal.ZERO)
                .interestRate(BigDecimal.ZERO)
                .build();

        when(accountRepository.countByUserId(any())).thenReturn(1L);
        when(accountNumberGenerator.generate()).thenReturn("ACC2500100003");
        when(accountRepository.existsByAccountNumber(any())).thenReturn(false);
        when(accountRepository.save(any())).thenReturn(currentAccount);

        AccountResponse response = accountService.createAccount(customer, request);

        assertThat(response).isNotNull();
        verify(accountRepository).save(argThat(acc ->
                acc.getInterestRate().compareTo(BigDecimal.ZERO) == 0
        ));
    }

    @Test
    @DisplayName("Should throw exception when user has 5 accounts already")
    void createAccount_MaxAccountsReached_ThrowsException() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountType(AccountType.SAVINGS);

        when(accountRepository.countByUserId(customer.getId())).thenReturn(5L);

        assertThatThrownBy(() -> accountService.createAccount(customer, request))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("Maximum");
    }

    @Test
    @DisplayName("Should retrieve account by number for owner")
    void getAccountByNumber_Owner_Success() {
        when(accountRepository.findByAccountNumber("ACC2500100001")).thenReturn(Optional.of(savingsAccount));

        AccountResponse response = accountService.getAccountByNumber("ACC2500100001", customer.getId());

        assertThat(response.getAccountNumber()).isEqualTo("ACC2500100001");
    }

    @Test
    @DisplayName("Should deny access to account for non-owner")
    void getAccountByNumber_NonOwner_ThrowsException() {
        when(accountRepository.findByAccountNumber("ACC2500100001")).thenReturn(Optional.of(savingsAccount));

        UUID anotherUserId = UUID.randomUUID();
        assertThatThrownBy(() -> accountService.getAccountByNumber("ACC2500100001", anotherUserId))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    @DisplayName("Should freeze account successfully")
    void freezeAccount_Success() {
        when(accountRepository.findByAccountNumber("ACC2500100001")).thenReturn(Optional.of(savingsAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.freezeAccount("ACC2500100001", UUID.randomUUID());

        assertThat(response.getStatus()).isEqualTo(AccountStatus.FROZEN);
    }

    @Test
    @DisplayName("Should throw exception when closing account with balance")
    void closeAccount_WithBalance_ThrowsException() {
        when(accountRepository.findByAccountNumber("ACC2500100001")).thenReturn(Optional.of(savingsAccount));

        // savingsAccount has balance of 25000
        assertThatThrownBy(() -> accountService.closeAccount("ACC2500100001", customer.getId()))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("remaining balance");
    }

    @Test
    @DisplayName("Should close zero-balance account successfully")
    void closeAccount_ZeroBalance_Success() {
        savingsAccount.setBalance(BigDecimal.ZERO);
        savingsAccount.setAvailableBalance(BigDecimal.ZERO);

        when(accountRepository.findByAccountNumber("ACC2500100001")).thenReturn(Optional.of(savingsAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.closeAccount("ACC2500100001", customer.getId());

        assertThat(response.getStatus()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    @DisplayName("Should get all accounts for a user")
    void getUserAccounts_ReturnsList() {
        when(accountRepository.findByUserId(customer.getId())).thenReturn(List.of(savingsAccount));

        List<AccountResponse> accounts = accountService.getUserAccounts(customer.getId());

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getAccountNumber()).isEqualTo("ACC2500100001");
    }

    @Test
    @DisplayName("Should throw ACCOUNT_FROZEN when operating on frozen account")
    void getActiveAccountOrThrow_FrozenAccount_ThrowsException() {
        savingsAccount.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findByAccountNumber("ACC2500100001")).thenReturn(Optional.of(savingsAccount));

        assertThatThrownBy(() -> accountService.getActiveAccountOrThrow("ACC2500100001"))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("frozen");
    }
}
