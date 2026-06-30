package com.banking.service;

import com.banking.dto.request.AddBeneficiaryRequest;
import com.banking.dto.response.BeneficiaryResponse;
import com.banking.entity.Beneficiary;
import com.banking.entity.User;
import com.banking.enums.OtpType;
import com.banking.exception.BankingException;
import com.banking.repository.BeneficiaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Manages the list of trusted payees (beneficiaries) for each user.
 *
 * A beneficiary is a saved recipient account that the user can transfer
 * money to. Adding a new beneficiary requires OTP verification (BENEFICIARY_ADD type)
 * — this prevents an attacker who briefly accesses the user's session from silently
 * adding their own account as a payee.
 *
 * Soft-delete pattern:
 *  Beneficiaries are never physically deleted from the database.
 *  Instead, the status is set to "DELETED", which preserves the audit trail of
 *  who was ever a beneficiary. Only "ACTIVE" beneficiaries are returned in listings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final OtpService otpService;
    private final AuditService auditService;

    /**
     * Adds a new beneficiary for the user, gated by OTP verification.
     *
     * Steps:
     *  1. Verify the OTP (BENEFICIARY_ADD type) — prevents unauthorised additions.
     *  2. Check for duplicate account number for this user (409 if already exists).
     *  3. Persist the beneficiary with ACTIVE status.
     *
     * @throws BankingException 400 INVALID_OTP, 409 if duplicate account number
     */
    @Transactional
    public BeneficiaryResponse addBeneficiary(AddBeneficiaryRequest request, User user) {
        // OTP required — high-risk action (adding a new payee)
        otpService.verify(user.getId(), OtpType.BENEFICIARY_ADD, request.getOtp());

        // Prevent adding the same account twice
        if (beneficiaryRepository.existsByUserIdAndAccountNumber(user.getId(), request.getAccountNumber())) {
            throw BankingException.conflict("Beneficiary with this account number already exists");
        }

        Beneficiary beneficiary = Beneficiary.builder()
                .user(user)
                .beneficiaryName(request.getBeneficiaryName())
                .accountNumber(request.getAccountNumber())
                .ifscCode(request.getIfscCode())
                .bankName(request.getBankName())
                .nickname(request.getNickname())  // friendly name like "Mom", "Landlord"
                .status("ACTIVE")
                .build();

        beneficiary = beneficiaryRepository.save(beneficiary);
        auditService.log(user.getId(), "BENEFICIARY_ADDED", "Beneficiary", beneficiary.getId().toString());
        return toResponse(beneficiary);
    }

    /**
     * Returns all ACTIVE beneficiaries for the user.
     * Soft-deleted entries (status="DELETED") are filtered out at the query level.
     */
    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> getBeneficiaries(UUID userId) {
        return beneficiaryRepository.findByUserIdAndStatus(userId, "ACTIVE")
                .stream().map(this::toResponse).toList();
    }

    /**
     * Soft-deletes a beneficiary (sets status to "DELETED").
     * The record is retained in the DB for audit purposes.
     *
     * @throws BankingException 404 if not found, 403 if not owner
     */
    @Transactional
    public void deleteBeneficiary(UUID beneficiaryId, UUID userId) {
        Beneficiary beneficiary = beneficiaryRepository.findById(beneficiaryId)
                .orElseThrow(() -> BankingException.notFound("Beneficiary", beneficiaryId.toString()));
        if (!beneficiary.getUser().getId().equals(userId)) {
            throw BankingException.forbidden("Access denied");
        }
        beneficiary.setStatus("DELETED"); // soft delete — preserves history
        beneficiaryRepository.save(beneficiary);
        auditService.log(userId, "BENEFICIARY_DELETED", "Beneficiary", beneficiaryId.toString());
    }

    /** Maps a Beneficiary entity to the API response DTO. */
    private BeneficiaryResponse toResponse(Beneficiary b) {
        return BeneficiaryResponse.builder()
                .id(b.getId())
                .beneficiaryName(b.getBeneficiaryName())
                .accountNumber(b.getAccountNumber())
                .ifscCode(b.getIfscCode())
                .bankName(b.getBankName())
                .nickname(b.getNickname())
                .status(b.getStatus())
                .createdAt(b.getCreatedAt())
                .build();
    }
}
