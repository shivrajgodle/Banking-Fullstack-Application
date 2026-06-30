package com.banking.entity;

import com.banking.enums.AccountStatus;
import com.banking.enums.AccountType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account extends BaseEntity {

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private AccountType accountType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Builder.Default
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Builder.Default
    @Column(name = "interest_rate", precision = 5, scale = 4)
    private BigDecimal interestRate = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "daily_limit", precision = 19, scale = 4)
    private BigDecimal dailyLimit = new BigDecimal("200000.0000");

    @Column(name = "nominee_name", length = 200)
    private String nomineeName;

    @Column(name = "nominee_relation", length = 100)
    private String nomineeRelation;

    @Builder.Default
    @Column(name = "branch_code", length = 20)
    private String branchCode = "MAIN001";

    @Builder.Default
    @Column(name = "ifsc_code", length = 20)
    private String ifscCode = "BANK0001";

    @Builder.Default
    @Column(name = "opened_date", nullable = false)
    private LocalDate openedDate = LocalDate.now();

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Column(name = "last_transaction_at")
    private LocalDateTime lastTransactionAt;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Transaction> transactions;
}
