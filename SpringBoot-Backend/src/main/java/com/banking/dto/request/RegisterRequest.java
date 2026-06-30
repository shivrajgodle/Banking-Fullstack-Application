package com.banking.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

@Data
public class RegisterRequest {

    @NotBlank @Size(min=2, max=100)
    private String firstName;

    @NotBlank @Size(min=2, max=100)
    private String lastName;

    @NotBlank @Email
    private String email;

    @NotBlank @Pattern(regexp="^[6-9]\\d{9}$", message="Invalid Indian mobile number")
    private String phoneNumber;

    @NotBlank @Size(min=8, message="Password must be at least 8 characters")
    @Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$",
             message="Password must contain uppercase, lowercase, number and special character")
    private String password;

    @NotNull @Past
    private LocalDate dateOfBirth;

    @NotBlank @Size(min=12, max=12, message="National ID must be 12 digits")
    private String nationalId;

    private String address;
    private String city;
    private String state;
    private String zipCode;
    private String country;
}
