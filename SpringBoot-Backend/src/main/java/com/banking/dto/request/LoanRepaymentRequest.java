package com.banking.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class LoanRepaymentRequest {
    @NotBlank private String loanNumber;
    @NotNull @DecimalMin("1.00") private BigDecimal amount;
    @NotBlank private String accountNumber;
    @NotBlank private String otp;
}
