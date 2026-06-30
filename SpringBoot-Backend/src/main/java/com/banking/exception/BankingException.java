package com.banking.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Central runtime exception for all business-logic and domain errors in the banking application.
 *
 * Design goals:
 *  1. Carry an HTTP status code so GlobalExceptionHandler can return the right response code.
 *  2. Carry a machine-readable errorCode string (e.g. "INSUFFICIENT_FUNDS") that the frontend
 *     can use for localisation or conditional UI logic.
 *  3. Provide named factory methods for every known error scenario — this avoids scattering
 *     "new BankingException(...)" calls with magic strings throughout the codebase.
 *
 * All service methods throw BankingException; GlobalExceptionHandler catches it and
 * converts it to a structured ErrorResponse JSON body.
 */
@Getter
public class BankingException extends RuntimeException {

    /** The HTTP status code to return in the response (e.g. 404, 400, 403). */
    private final HttpStatus status;

    /**
     * A short, uppercase code identifying the error type.
     * Clients can branch on this without parsing the human-readable message.
     * Examples: "NOT_FOUND", "INSUFFICIENT_FUNDS", "DAILY_LIMIT_EXCEEDED"
     */
    private final String errorCode;

    /** Full constructor — message, HTTP status, and custom error code. */
    public BankingException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    /** Convenience constructor — derives the errorCode from the status name. */
    public BankingException(String message, HttpStatus status) {
        this(message, status, status.name());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Named factory methods — one for each distinct error scenario
    // Using static factory methods keeps call sites readable:
    //   throw BankingException.notFound("Account", accountNumber);
    // instead of:
    //   throw new BankingException("Account not found: " + accountNumber, HttpStatus.NOT_FOUND, "NOT_FOUND");
    // ──────────────────────────────────────────────────────────────────────────

    /** 404 — the requested resource (account, loan, user, etc.) does not exist. */
    public static BankingException notFound(String resource, String id) {
        return new BankingException(resource + " not found: " + id, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    /** 400 — generic bad request with a custom message (validation, business rule violation). */
    public static BankingException badRequest(String message) {
        return new BankingException(message, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }

    /** 403 — the authenticated user does not have permission for this action. */
    public static BankingException forbidden(String message) {
        return new BankingException(message, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    /** 409 — a duplicate resource would be created (e.g. duplicate beneficiary). */
    public static BankingException conflict(String message) {
        return new BankingException(message, HttpStatus.CONFLICT, "CONFLICT");
    }

    /** 400 — account balance is too low to complete the requested transaction. */
    public static BankingException insufficientFunds() {
        return new BankingException("Insufficient funds in account", HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS");
    }

    /** 403 — the target account is frozen and cannot send or receive funds. */
    public static BankingException accountFrozen() {
        return new BankingException("Account is frozen", HttpStatus.FORBIDDEN, "ACCOUNT_FROZEN");
    }

    /** 400 — the transaction would exceed the account's configured daily outflow limit. */
    public static BankingException dailyLimitExceeded() {
        return new BankingException("Daily transaction limit exceeded", HttpStatus.BAD_REQUEST, "DAILY_LIMIT_EXCEEDED");
    }

    /** 400 — the OTP provided is wrong, already used, or has expired. */
    public static BankingException invalidOtp() {
        return new BankingException("Invalid or expired OTP", HttpStatus.BAD_REQUEST, "INVALID_OTP");
    }

    /** 409 — attempting to register with an email, phone, or national ID that is already in use. */
    public static BankingException accountAlreadyExists(String field) {
        return new BankingException(field + " is already registered", HttpStatus.CONFLICT, "DUPLICATE_ENTRY");
    }
}
