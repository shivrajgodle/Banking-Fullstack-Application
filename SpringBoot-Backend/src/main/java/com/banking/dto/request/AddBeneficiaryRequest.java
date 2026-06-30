package com.banking.dto.request;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddBeneficiaryRequest {
    @NotBlank
    private String beneficiaryName;

    @NotBlank
    private String accountNumber;

    @NotBlank
    private String ifscCode;

    private String bankName;
    private String nickname;

    @NotBlank
    private String otp;
}
