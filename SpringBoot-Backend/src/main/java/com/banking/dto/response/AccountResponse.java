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
public class AccountResponse {
    private UUID id;
    private String accountNumber;
    private String ownerName;
    private AccountType accountType;
    private AccountStatus status;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private String currency;
    private BigDecimal interestRate;
    private BigDecimal dailyLimit;
    private String nomineeName;
    private String nomineeRelation;
    private String branchCode;
    private String ifscCode;
    private LocalDate openedDate;
    private LocalDateTime lastTransactionAt;
    private LocalDateTime createdAt;
}
