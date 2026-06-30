package com.banking.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception-to-HTTP-response translator for the entire application.
 *
 * @RestControllerAdvice intercepts exceptions thrown from any @RestController
 * and converts them to structured JSON error responses before they reach the client.
 * This prevents raw stack traces or default Spring error pages from leaking to callers.
 *
 * Handler priority: Spring picks the most specific @ExceptionHandler first.
 * If no specific handler matches, the catch-all handleAll() at the bottom
 * returns a 500 Internal Server Error.
 *
 * Error response shape: all handlers call buildResponse() which builds a
 * consistent ErrorResponse JSON body:
 *   {
 *     "timestamp": "2026-04-11T12:34:56",
 *     "status": 404,
 *     "errorCode": "NOT_FOUND",
 *     "message": "Account not found: ACC26000001",
 *     "path": "/api/v1/accounts/ACC26000001"
 *   }
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles all domain/business exceptions thrown by service methods.
     * BankingException carries its own status and errorCode, so the handler
     * just mirrors them into the response.
     */
    @ExceptionHandler(BankingException.class)
    public ResponseEntity<ErrorResponse> handleBankingException(BankingException ex, WebRequest request) {
        log.error("BankingException: {} | Path: {}", ex.getMessage(), request.getDescription(false));
        return buildResponse(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request);
    }

    /**
     * Handles @Valid / @Validated annotation failures on request body fields.
     * Collects all field-level validation errors into a map so the client knows
     * exactly which fields are wrong, e.g.:
     *   { "email": "must be a valid email", "password": "size must be between 8 and 50" }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        // Iterate all binding errors; cast to FieldError to get the field name
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode("VALIDATION_ERROR")
                .message("Validation failed")
                .path(request.getDescription(false).replace("uri=", ""))
                .validationErrors(errors) // include per-field details
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Thrown by Spring Security's DaoAuthenticationProvider when the password
     * doesn't match the stored BCrypt hash during login.
     * Returns 401 Unauthorized with a generic message (don't reveal why auth failed).
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, WebRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password", request);
    }

    /**
     * Thrown when a user whose account status is LOCKED tries to log in.
     * Spring Security sets this based on isAccountNonLocked() in the User entity.
     */
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLocked(LockedException ex, WebRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "Account is locked. Please try later.", request);
    }

    /**
     * Thrown when a user whose account status is not ACTIVE (e.g. SUSPENDED) tries to log in.
     * Spring Security calls isEnabled() on the User entity to detect this.
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(DisabledException ex, WebRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Account is disabled.", request);
    }

    /**
     * Thrown by @PreAuthorize or Spring Security's role checks when an authenticated
     * user tries to access a resource above their permission level (e.g. a CUSTOMER
     * attempting to hit an /admin/** endpoint).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission to perform this action", request);
    }

    /**
     * Catch-all for any unexpected runtime exceptions not handled above.
     * Logs the full stack trace for debugging but returns a safe, generic message
     * to the client so internal details are not exposed.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(Exception ex, WebRequest request) {
        log.error("Unhandled exception: ", ex); // full stack trace in server logs
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", request);
    }

    /**
     * Shared builder for all error responses — keeps response shape consistent
     * across every handler method.
     */
    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String errorCode,
                                                         String message, WebRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .errorCode(errorCode)
                .message(message)
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        return ResponseEntity.status(status).body(response);
    }
}
