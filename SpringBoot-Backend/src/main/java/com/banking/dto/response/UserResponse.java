package com.banking.dto.response;
import com.banking.enums.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private String nationalId;
    private String address;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private UserRole role;
    private UserStatus status;
    private KycStatus kycStatus;
    private boolean emailVerified;
    private boolean phoneVerified;
    private LocalDateTime lastLogin;
    private String profileImage;
    private LocalDateTime createdAt;
}
