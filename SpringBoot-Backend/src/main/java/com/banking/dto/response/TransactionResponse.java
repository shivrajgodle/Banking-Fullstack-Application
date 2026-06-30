package com.banking.dto.response;
import com.banking.enums.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionResponse {
    private UUID id;
    private String transactionRef;
    private String accountNumber;
    private String counterpartyAccountNumber;
    private TransactionType transactionType;
    private BigDecimal amount;
    private String currency;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private TransactionStatus status;
    private String description;
    private String referenceNumber;
    private String paymentMode;
    private String channel;
    private String failureReason;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
