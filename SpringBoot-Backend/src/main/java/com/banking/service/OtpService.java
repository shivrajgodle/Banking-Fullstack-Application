package com.banking.service;

import com.banking.entity.Otp;
import com.banking.entity.User;
import com.banking.enums.OtpType;
import com.banking.exception.BankingException;
import com.banking.repository.OtpRepository;
import com.banking.util.OtpGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Generates, sends, and verifies One-Time Passwords (OTPs).
 *
 * OTP types (OtpType enum) determine what operation the OTP authorises:
 *   LOGIN            — second-factor for login (not currently wired in the UI, optional)
 *   TRANSACTION      — required before withdrawals and fund transfers
 *   RESET_PASSWORD   — password change flow
 *   EMAIL_VERIFY     — verifying the user's email address
 *   PHONE_VERIFY     — verifying the user's phone number
 *   BENEFICIARY_ADD  — adding a new payee (high-risk action)
 *
 * Security measures:
 *  1. Previous OTPs of the same type are invalidated before a new one is issued,
 *     so only one valid OTP per type can exist per user at a time.
 *  2. OTPs expire after a configurable period (default 10 minutes).
 *  3. A maximum of 3 verification attempts is allowed; on the 4th attempt the OTP
 *     is automatically consumed (used=true) and must be re-requested.
 *  4. The OTP code is generated using SecureRandom (see OtpGenerator).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpRepository otpRepository;
    private final OtpGenerator otpGenerator;
    private final EmailService emailService;

    /** How many minutes the OTP remains valid after generation. Defaults to 10. */
    @Value("${banking.otp.expiry-minutes:10}")
    private int otpExpiryMinutes;

    /**
     * Generates a new OTP for the given user and type, saves it to the DB,
     * and dispatches it to the user's registered email asynchronously.
     *
     * Before generating, all existing (unused, non-expired) OTPs of the same
     * type for this user are marked as used to prevent the user from having
     * multiple valid codes simultaneously.
     *
     * @param user    the authenticated user requesting an OTP
     * @param otpType the operation this OTP is intended to authorise
     * @return the generated code (primarily for test assertions; in prod,
     *         the code should travel only to the user's email, not the response body)
     */
    @Transactional
    public String generateAndSend(User user, OtpType otpType) {
        // Invalidate any currently active OTPs of the same type for this user
        otpRepository.invalidateOtps(user.getId(), otpType);

        String code = otpGenerator.generate(); // cryptographically random, zero-padded

        Otp otp = Otp.builder()
                .user(user)
                .otpCode(code)
                .otpType(otpType)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes))
                .build();
        otpRepository.save(otp);

        // Send email asynchronously so this method returns quickly
        emailService.sendOtp(user.getEmail(), user.getFirstName(), code, otpType);

        log.info("OTP generated for user {} type {}", user.getId(), otpType);
        return code;
    }

    /**
     * Verifies an OTP code submitted by the user.
     *
     * Verification steps:
     *  1. Look up the most recent unused, non-expired OTP for this user and type.
     *     Throws BankingException (INVALID_OTP) if none found.
     *  2. Increment the attempt counter.
     *  3. If attempts > 3, mark the OTP as consumed and throw — force re-request.
     *  4. If the code doesn't match, save the incremented attempt count and throw.
     *  5. If correct, mark the OTP as used (prevents replay attacks).
     *
     * @param userId  the user whose OTP is being verified
     * @param otpType the type of operation being confirmed
     * @param code    the code the user submitted
     * @throws BankingException (400 INVALID_OTP) for wrong code, too many attempts, or expired
     */
    @Transactional
    public void verify(UUID userId, OtpType otpType, String code) {
        // Find a valid (not used, not expired) OTP
        Otp otp = otpRepository
                .findByUserIdAndOtpTypeAndUsedFalseAndExpiresAtAfter(userId, otpType, LocalDateTime.now())
                .orElseThrow(BankingException::invalidOtp);

        otp.setAttempts(otp.getAttempts() + 1);

        // Brute-force protection: after 3 wrong attempts, invalidate the OTP
        if (otp.getAttempts() > 3) {
            otp.setUsed(true);
            otpRepository.save(otp);
            throw BankingException.badRequest("OTP attempts exceeded. Please request a new OTP.");
        }

        // Wrong code — save the incremented attempt count then throw
        if (!otp.getOtpCode().equals(code)) {
            otpRepository.save(otp);
            throw BankingException.invalidOtp();
        }

        // Correct code — mark as used to prevent replay
        otp.setUsed(true);
        otpRepository.save(otp);
    }
}
