package com.banking.service;

import com.banking.dto.request.LoginRequest;
import com.banking.dto.request.RegisterRequest;
import com.banking.dto.response.AuthResponse;
import com.banking.dto.response.UserResponse;
import com.banking.entity.RefreshToken;
import com.banking.entity.User;
import com.banking.enums.NotificationType;
import com.banking.enums.UserRole;
import com.banking.enums.UserStatus;
import com.banking.exception.BankingException;
import com.banking.repository.RefreshTokenRepository;
import com.banking.repository.UserRepository;
import com.banking.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handles all authentication-related business logic:
 * user registration, login, token refresh, and logout.
 *
 * ─── Token strategy ───────────────────────────────────────────────────────────
 * The application uses a two-token system:
 *
 *  ACCESS TOKEN (JWT)
 *    - Short-lived (e.g. 15 minutes), stateless, signed JWT
 *    - Sent in Authorization header for every API call
 *    - Contains role, userId, name — no DB lookup needed per request
 *
 *  REFRESH TOKEN
 *    - Long-lived (7 days), stored as a row in the `refresh_tokens` table
 *    - Used only to obtain a new access token when the old one expires
 *    - On each use, the old refresh token is revoked and a new one is issued
 *      (refresh token rotation) to detect stolen tokens
 *    - On login, ALL previous refresh tokens for the user are revoked first,
 *      ensuring only one active session at a time per login event
 *
 * ─── Post-registration event ──────────────────────────────────────────────────
 * After a new user is saved, a UserRegisteredEvent is published. The listener
 * fires AFTER the database transaction commits (@TransactionalEventListener),
 * guaranteeing the user row exists before the welcome email and notification
 * are inserted. This avoids foreign-key constraint failures that would occur
 * if those async operations ran before the commit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Internal event published at the end of register() after the user is committed.
     * Using a record makes it an immutable value object — safe to pass across threads.
     * Only carries the data needed by the post-registration handlers.
     */
    public record UserRegisteredEvent(UUID userId, String email, String firstName) {}

    // ── Register ──────────────────────────────────────────────────────────────

    /**
     * Registers a new customer account.
     *
     * Steps:
     *  1. Check uniqueness of email, phone, and national ID (409 Conflict if taken)
     *  2. Build and persist the User with a BCrypt-hashed password
     *  3. Publish UserRegisteredEvent — the listener fires after commit (see below)
     *
     * @throws BankingException 409 if email, phone, or nationalId already exists
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Uniqueness guards — each field has a UNIQUE constraint in the DB,
        // but checking here provides a better error message than a constraint violation
        if (userRepository.existsByEmail(request.getEmail())) {
            throw BankingException.accountAlreadyExists("Email");
        }
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw BankingException.accountAlreadyExists("Phone number");
        }
        if (userRepository.existsByNationalId(request.getNationalId())) {
            throw BankingException.accountAlreadyExists("National ID");
        }

        // Build the User entity — BCrypt-hash the password before storage
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .passwordHash(passwordEncoder.encode(request.getPassword())) // never store plain text
                .dateOfBirth(request.getDateOfBirth())
                .nationalId(request.getNationalId())
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .zipCode(request.getZipCode())
                .country(request.getCountry() != null ? request.getCountry() : "India")
                .role(UserRole.CUSTOMER)          // new self-registrations are always CUSTOMER
                .status(UserStatus.ACTIVE)
                .build();

        user = userRepository.save(user);

        // Publish the event — the @TransactionalEventListener fires only AFTER this
        // transaction commits, so the user row is definitely in the DB before audit
        // and notification services try to reference it (avoids FK race conditions)
        eventPublisher.publishEvent(
                new UserRegisteredEvent(user.getId(), user.getEmail(), user.getFirstName()));

        log.info("New user registered: {}", user.getEmail());
        return toUserResponse(user);
    }

    /**
     * Post-registration handler — runs in a NEW thread after the register()
     * transaction has fully committed to the database.
     *
     * Why AFTER_COMMIT?
     *  If register() rolls back (e.g. a DB error), we must NOT send a welcome
     *  email or create a notification for a user that doesn't actually exist.
     *  AFTER_COMMIT ensures we only send side effects for successful registrations.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        // Re-load the user inside this new execution context
        User user = userRepository.findById(event.userId()).orElse(null);
        if (user == null) {
            log.warn("onUserRegistered: user {} not found after commit", event.userId());
            return;
        }
        // These are fire-and-forget side effects — failures here don't affect the user record
        emailService.sendWelcomeEmail(event.email(), event.firstName());
        notificationService.send(user, "Welcome!",
                "Your account has been created successfully.", NotificationType.ACCOUNT);
        auditService.log(event.userId(), "USER_REGISTERED", "User", event.userId().toString());
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Authenticates a user and returns a fresh access + refresh token pair.
     *
     * Steps:
     *  1. Delegate credential check to Spring Security's AuthenticationManager
     *     (triggers UserDetailsServiceImpl + BCrypt comparison).
     *     Throws BadCredentialsException if wrong password (handled by GlobalExceptionHandler).
     *  2. Reject suspended accounts explicitly (LOCKED accounts are handled by Spring Security
     *     itself via isAccountNonLocked()).
     *  3. Reset failed login counter and record last login time.
     *  4. Revoke all existing refresh tokens for this user (single-session policy on login).
     *  5. Issue new access token + refresh token and persist the refresh token.
     *
     * @throws BankingException 403 if the account is suspended
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Throws BadCredentialsException (401) if email/password are wrong
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> BankingException.notFound("User", request.getEmail()));

        // Spring Security handles LOCKED status, but SUSPENDED requires a manual check
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw BankingException.forbidden("Account is suspended. Contact support.");
        }

        // Reset failed-login counter and record login time
        user.setFailedLoginAttempts(0);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        // Revoke all existing refresh tokens so old sessions can no longer refresh
        refreshTokenRepository.revokeAllUserTokens(user.getId());

        // Issue new JWT access token (short-lived) and refresh token (long-lived)
        String accessToken     = jwtUtil.generateToken(user);
        String refreshTokenStr = jwtUtil.generateRefreshToken(user);

        // Persist refresh token — stored in DB so it can be explicitly revoked on logout
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenStr)
                .expiresAt(LocalDateTime.now().plusDays(7)) // matches jwt.refresh-expiration
                .build();
        refreshTokenRepository.save(refreshToken);

        auditService.log(user.getId(), "USER_LOGIN", "User", user.getId().toString());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpirationTime()) // milliseconds until access token expires
                .user(toUserResponse(user))
                .build();
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────

    /**
     * Issues a new access token + refresh token in exchange for a valid, non-expired,
     * non-revoked refresh token (token rotation pattern).
     *
     * Token rotation:
     *  - The presented refresh token is immediately marked as revoked
     *  - A brand-new refresh token is issued and stored
     *  - If a stolen refresh token is replayed after the legitimate user has already
     *    rotated it, the system detects it (token is revoked) and returns 400
     *
     * @param token the raw refresh token string from the client
     * @throws BankingException 400 if token is invalid, revoked, or expired
     */
    @Transactional
    public AuthResponse refreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> BankingException.badRequest("Invalid refresh token"));

        // Detect replayed / stolen tokens
        if (refreshToken.isRevoked()) {
            throw BankingException.badRequest("Refresh token has been revoked");
        }
        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw BankingException.badRequest("Refresh token has expired");
        }

        User user              = refreshToken.getUser();
        String newAccessToken  = jwtUtil.generateToken(user);
        String newRefreshStr   = jwtUtil.generateRefreshToken(user);

        // Invalidate the used refresh token (rotation)
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        // Issue and persist a fresh refresh token
        RefreshToken newRefreshToken = RefreshToken.builder()
                .user(user)
                .token(newRefreshStr)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(newRefreshToken);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshStr)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpirationTime())
                .user(toUserResponse(user))
                .build();
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    /**
     * Revokes all refresh tokens for the user, effectively invalidating their session.
     *
     * Note: JWTs are stateless, so the access token itself cannot be "revoked" directly.
     * It will remain technically valid until it expires naturally (typically 15 minutes).
     * For stricter security, a token blacklist or short expiry is recommended.
     */
    @Transactional
    public void logout(User user) {
        refreshTokenRepository.revokeAllUserTokens(user.getId());
        auditService.log(user.getId(), "USER_LOGOUT", "User", user.getId().toString());
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    /**
     * Converts a User entity to a UserResponse DTO for API responses.
     * Deliberately excludes sensitive fields: passwordHash, failedLoginAttempts, lockedUntil.
     */
    public UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .dateOfBirth(user.getDateOfBirth())
                .nationalId(user.getNationalId())
                .address(user.getAddress())
                .city(user.getCity())
                .state(user.getState())
                .zipCode(user.getZipCode())
                .country(user.getCountry())
                .role(user.getRole())
                .status(user.getStatus())
                .kycStatus(user.getKycStatus())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .lastLogin(user.getLastLogin())
                .profileImage(user.getProfileImage())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
