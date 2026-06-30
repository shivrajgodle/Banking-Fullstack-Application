package com.banking.dto.response;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FixedDepositResponse {
    private UUID id;
    private String fdNumber;
    private String accountNumber;
    private BigDecimal principalAmount;
    private BigDecimal interestRate;
    private int tenureMonths;
    private BigDecimal maturityAmount;
    private LocalDate maturityDate;
    private String interestPayout;
    private boolean autoRenew;
    private String status;
    private LocalDate openedDate;
    private LocalDateTime createdAt;
}
