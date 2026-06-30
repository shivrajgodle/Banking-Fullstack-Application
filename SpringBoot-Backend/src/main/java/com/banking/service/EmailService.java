package com.banking.service;

import com.banking.enums.OtpType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Sends transactional emails (OTPs, transaction alerts, welcome messages) via SMTP.
 *
 * All public methods are @Async — they run on the "Banking-Async-" thread pool
 * (configured in AsyncConfig) so HTTP request threads are never blocked waiting
 * for SMTP delivery.
 *
 * Mail-disabled mode:
 *  If banking.mail.enabled=false OR spring.mail.username is blank (no SMTP credentials),
 *  the service skips actual sending and logs what it would have sent.
 *  This makes local development and tests work without a real mail server.
 *
 * Error handling:
 *  All send methods catch exceptions internally and log them.
 *  A mail delivery failure must never cause the calling transaction to roll back —
 *  an OTP email failure shouldn't prevent a user from receiving their OTP in
 *  a retry, and a transaction alert failure doesn't undo the transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    /** Set to false in application.yml to disable all outgoing mail (e.g. dev/test). */
    @Value("${banking.mail.enabled:true}")
    private boolean mailEnabled;

    /** The "From" address — also used to detect whether SMTP is configured. */
    @Value("${spring.mail.username:}")
    private String fromAddress;

    // ── OTP Email ─────────────────────────────────────────────────────────────

    /**
     * Sends an OTP code to the user's registered email address.
     *
     * Subject line varies by OtpType (e.g. "Transaction OTP - Banking").
     * Message includes a 10-minute validity reminder and a fraud warning.
     *
     * @param toEmail  recipient address
     * @param name     user's first name (for personalised greeting)
     * @param otp      the generated OTP code (6 digits by default)
     * @param type     context for the OTP (login, transaction, password reset, etc.)
     */
    @Async
    public void sendOtp(String toEmail, String name, String otp, OtpType type) {
        if (isMailDisabled()) {
            // Log the OTP when mail is disabled so developers can still test the flow
            log.info("[MAIL DISABLED] OTP for {} | type={} | otp={}", toEmail, type, otp);
            return;
        }
        try {
            String subject = getOtpSubject(type);
            String body = String.format(
                "Dear %s,%n%n" +
                "Your OTP for %s is: %s%n%n" +
                "This OTP is valid for 10 minutes. Do not share it with anyone.%n%n" +
                "If you did not request this OTP, please contact support immediately.%n%n" +
                "Regards,%nBanking Support Team",
                name, type.name().replace("_", " "), otp);
            sendEmail(toEmail, subject, body);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ── Transaction Alert ─────────────────────────────────────────────────────

    /**
     * Sends a transaction notification after a deposit, withdrawal, or transfer.
     *
     * Includes the transaction reference, amount, type, and available balance
     * so the user has a paper trail in their inbox for every movement of funds.
     *
     * @param txnRef   unique transaction reference number (e.g. TXN17439...)
     * @param amount   the transaction amount
     * @param balance  the available balance after the transaction
     * @param type     human-readable label e.g. "DEPOSIT", "WITHDRAWAL", "TRANSFER (DEBIT)"
     */
    @Async
    public void sendTransactionAlert(String toEmail, String name, String txnRef,
                                     BigDecimal amount, BigDecimal balance, String type) {
        if (isMailDisabled()) {
            log.info("[MAIL DISABLED] Transaction alert for {} | type={} | amount={}", toEmail, type, amount);
            return;
        }
        try {
            String subject = "Transaction Alert - " + txnRef;
            String body = String.format(
                "Dear %s,%n%n" +
                "A %s transaction of Rs.%.2f has been processed on your account.%n" +
                "Transaction Reference: %s%n" +
                "Available Balance: Rs.%.2f%n%n" +
                "If you did not authorize this transaction, please contact support immediately.%n%n" +
                "Regards,%nBanking Team",
                name, type, amount, txnRef, balance);
            sendEmail(toEmail, subject, body);
        } catch (Exception e) {
            log.error("Failed to send transaction email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ── Welcome Email ─────────────────────────────────────────────────────────

    /**
     * Sent automatically after a successful user registration (via UserRegisteredEvent).
     * Prompts the new customer to complete KYC to unlock all features.
     */
    @Async
    public void sendWelcomeEmail(String toEmail, String name) {
        if (isMailDisabled()) {
            log.info("[MAIL DISABLED] Welcome email would be sent to: {}", toEmail);
            return;
        }
        try {
            String subject = "Welcome to Banking - Account Created Successfully";
            String body = String.format(
                "Dear %s,%n%n" +
                "Welcome to Banking! Your account has been created successfully.%n%n" +
                "Please complete your KYC verification to unlock all banking features.%n%n" +
                "Regards,%nBanking Team",
                name);
            sendEmail(toEmail, subject, body);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ── Internal Helpers ──────────────────────────────────────────────────────

    /**
     * Low-level SMTP dispatch using Spring's JavaMailSender.
     * SimpleMailMessage is sufficient for plain-text emails.
     * For HTML emails, JavaMailMessage with MimeMessageHelper would be needed.
     */
    private void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        message.setFrom(fromAddress);
        mailSender.send(message);
        log.info("Email sent to {} | subject: {}", to, subject);
    }

    /**
     * Returns true if mail sending should be skipped.
     * Skips if: explicitly disabled in config, OR no from-address configured
     * (indicates SMTP credentials are missing).
     */
    private boolean isMailDisabled() {
        return !mailEnabled || fromAddress == null || fromAddress.isBlank();
    }

    /**
     * Returns an appropriate email subject line for each OTP type.
     * Keeping subjects consistent makes it easy for users to search their inbox.
     */
    private String getOtpSubject(OtpType type) {
        return switch (type) {
            case LOGIN           -> "Login OTP - Banking";
            case TRANSACTION     -> "Transaction OTP - Banking";
            case RESET_PASSWORD  -> "Password Reset OTP - Banking";
            case EMAIL_VERIFY    -> "Email Verification OTP - Banking";
            case PHONE_VERIFY    -> "Phone Verification OTP - Banking";
            case BENEFICIARY_ADD -> "Beneficiary Addition OTP - Banking";
        };
    }
}
