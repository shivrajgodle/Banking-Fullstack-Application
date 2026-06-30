package com.banking.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class WithdrawalRequest {
    @NotBlank private String accountNumber;
    @NotNull @DecimalMin("1.00") private BigDecimal amount;
    @NotBlank private String otp;
    private String description;
    private String paymentMode = "CASH";
}
