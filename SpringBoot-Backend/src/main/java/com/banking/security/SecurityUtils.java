package com.banking.security;

import com.banking.entity.User;
import com.banking.exception.BankingException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Convenience utility for extracting the currently authenticated user from
 * the Spring Security context inside service and controller methods.
 *
 * Why this class?
 *  Controllers need the User entity (not just a username string) to pass into
 *  service methods for ownership checks and audit logging. This utility centralises
 *  the cast so each controller doesn't duplicate the same boilerplate.
 *
 * How it works:
 *  The SecurityContextHolder holds the Authentication object that was set by
 *  JwtAuthenticationFilter. The principal stored there is the User entity
 *  (loaded from the DB via UserDetailsServiceImpl and stored directly as the
 *  principal since User implements UserDetails).
 *
 * Thread safety: SecurityContextHolder uses ThreadLocal storage by default,
 * so each request thread sees only its own authentication.
 *
 * Usage:
 *   User user = SecurityUtils.getCurrentUser();
 */
public final class SecurityUtils {

    /** Private constructor — this is a static utility class, not meant to be instantiated. */
    private SecurityUtils() {}

    /**
     * Returns the currently authenticated User from the SecurityContext.
     *
     * @return the authenticated User entity
     * @throws BankingException (403 Forbidden) if:
     *   - No authentication is present in the context (unauthenticated request slipped through)
     *   - The principal is not a User instance (misconfiguration or unexpected auth type)
     */
    public static User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // This guard should never trigger in normal operation because SecurityConfig
        // enforces authentication on all non-public endpoints. It's a safety net.
        if (auth == null || !auth.isAuthenticated()) {
            throw BankingException.forbidden("Not authenticated");
        }

        Object principal = auth.getPrincipal();

        // The User entity implements UserDetails and was stored as the principal
        // by JwtAuthenticationFilter. Pattern match (Java 16+) for a clean cast.
        if (principal instanceof User user) {
            return user;
        }

        // Should never happen unless a different authentication mechanism is in play
        throw BankingException.forbidden("Invalid authentication principal");
    }
}
