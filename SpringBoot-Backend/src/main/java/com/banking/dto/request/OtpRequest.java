package com.banking.dto.request;
import com.banking.enums.OtpType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OtpRequest {
    @NotNull private OtpType otpType;
}
