package com.banking.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min=8)
    @Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$",
             message="Password must contain uppercase, lowercase, number and special character")
    private String newPassword;

    @NotBlank
    private String confirmPassword;
}
