package com.banking.dto.request;
import com.banking.enums.AccountType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAccountRequest {
    @NotNull
    private AccountType accountType;
    private String nomineeName;
    private String nomineeRelation;
    private String currency = "INR";
}
