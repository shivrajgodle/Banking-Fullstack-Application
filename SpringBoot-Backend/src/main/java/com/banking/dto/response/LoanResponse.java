package com.banking.dto.response;
import com.banking.enums.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanResponse {
    private UUID id;
    private String loanNumber;
    private String borrowerName;
    private LoanType loanType;
    private BigDecimal principalAmount;
    private BigDecimal outstandingAmount;
    private BigDecimal interestRate;
    private int tenureMonths;
    private BigDecimal emiAmount;
    private int emiDay;
    private BigDecimal totalInterest;
    private BigDecimal totalPayable;
    private BigDecimal paidAmount;
    private LoanStatus status;
    private String purpose;
    private LocalDate disbursedDate;
    private LocalDate maturityDate;
    private LocalDate nextEmiDate;
    private BigDecimal overdueAmount;
    private int overdueDays;
    private LocalDateTime createdAt;
}
