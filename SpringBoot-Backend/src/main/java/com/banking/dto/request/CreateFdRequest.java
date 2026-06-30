package com.banking.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateFdRequest {
    @NotBlank
    private String accountNumber;

    @NotNull
    @DecimalMin("1000.00")
    private BigDecimal principalAmount;

    @NotNull
    @Min(1)
    @Max(120)
    private Integer tenureMonths;

    private boolean autoRenew = false;
    private String interestPayout = "ON_MATURITY";
}
