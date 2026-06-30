package com.banking.entity;

import com.banking.enums.LoanStatus;
import com.banking.enums.LoanType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "loans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Loan extends BaseEntity {

    @Column(name = "loan_number", nullable = false, unique = true, length = 30)
    private String loanNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false, length = 50)
    private LoanType loanType;

    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalAmount;

    @Column(name = "outstanding_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingAmount;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "tenure_months", nullable = false)
    private int tenureMonths;

    @Column(name = "emi_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal emiAmount;

    @Builder.Default
    @Column(name = "emi_day", nullable = false)
    private int emiDay = 5;

    @Builder.Default
    @Column(name = "disbursed_amount", precision = 19, scale = 4)
    private BigDecimal disbursedAmount = BigDecimal.ZERO;

    @Column(name = "total_interest", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalInterest;

    @Column(name = "total_payable", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalPayable;

    @Builder.Default
    @Column(name = "paid_amount", precision = 19, scale = 4)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LoanStatus status = LoanStatus.PENDING;

    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "disbursed_date")
    private LocalDate disbursedDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "next_emi_date")
    private LocalDate nextEmiDate;

    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;

    @Builder.Default
    @Column(name = "overdue_amount", precision = 19, scale = 4)
    private BigDecimal overdueAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "overdue_days")
    private int overdueDays = 0;

    @Builder.Default
    @Column(name = "penalty_rate", precision = 5, scale = 4)
    private BigDecimal penaltyRate = new BigDecimal("0.0200");

    @Column(name = "collateral_type", length = 100)
    private String collateralType;

    @Column(name = "collateral_value", precision = 19, scale = 4)
    private BigDecimal collateralValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
}
