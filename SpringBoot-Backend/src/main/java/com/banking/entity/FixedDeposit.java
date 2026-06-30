package com.banking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fixed_deposits")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FixedDeposit extends BaseEntity {

    @Column(name = "fd_number", nullable = false, unique = true, length = 30)
    private String fdNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalAmount;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "tenure_months", nullable = false)
    private int tenureMonths;

    @Column(name = "maturity_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal maturityAmount;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Builder.Default
    @Column(name = "interest_payout", length = 30)
    private String interestPayout = "ON_MATURITY";

    @Builder.Default
    @Column(name = "auto_renew")
    private boolean autoRenew = false;

    @Builder.Default
    @Column(name = "status", length = 30)
    private String status = "ACTIVE";

    @Builder.Default
    @Column(name = "opened_date", nullable = false)
    private LocalDate openedDate = LocalDate.now();

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Builder.Default
    @Column(name = "premature_penalty", precision = 5, scale = 4)
    private BigDecimal prematurePenalty = new BigDecimal("0.0100");
}
