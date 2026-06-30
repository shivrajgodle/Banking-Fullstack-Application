package com.banking.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class DepositRequest {
    @NotBlank
    private String accountNumber;

    @NotNull
    @DecimalMin("1.00")
    @DecimalMax("1000000.00")
    private BigDecimal amount;

    private String description;
    private String paymentMode = "CASH";
    private String referenceNumber;
}
