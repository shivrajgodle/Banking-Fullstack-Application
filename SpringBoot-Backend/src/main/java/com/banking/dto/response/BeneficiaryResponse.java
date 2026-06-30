package com.banking.dto.response;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BeneficiaryResponse {
    private UUID id;
    private String beneficiaryName;
    private String accountNumber;
    private String ifscCode;
    private String bankName;
    private String nickname;
    private String status;
    private LocalDateTime createdAt;
}
