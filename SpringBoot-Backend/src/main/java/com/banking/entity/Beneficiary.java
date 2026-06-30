package com.banking.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "beneficiaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Beneficiary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "beneficiary_name", nullable = false, length = 200)
    private String beneficiaryName;

    @Column(name = "account_number", nullable = false, length = 30)
    private String accountNumber;

    @Column(name = "ifsc_code", length = 20)
    private String ifscCode;

    @Column(name = "bank_name", length = 200)
    private String bankName;

    @Column(name = "nickname", length = 100)
    private String nickname;

    @Builder.Default
    @Column(name = "status", length = 30)
    private String status = "ACTIVE";
}
