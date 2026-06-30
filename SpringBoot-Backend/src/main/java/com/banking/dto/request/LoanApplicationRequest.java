package com.banking.dto.request;
import com.banking.enums.LoanType;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class LoanApplicationRequest {
    @NotNull
    private LoanType loanType;

    @NotNull
    @DecimalMin("10000.00")
    @DecimalMax("50000000.00")
    private BigDecimal principalAmount;

    @NotNull
    @Min(6)
    @Max(360)
    private Integer tenureMonths;

    @NotBlank
    private String accountNumber;
    private String purpose;
    private String collateralType;
    private BigDecimal collateralValue;
}
