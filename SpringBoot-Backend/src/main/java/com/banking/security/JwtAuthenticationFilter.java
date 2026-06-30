package com.banking.security;

import com.banking.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * JWT-based authentication filter that runs exactly once per HTTP request
 * (Spring's OncePerRequestFilter guarantees this, even with forwarded requests).
 *
 * Flow for each request:
 *  1. Read the "Authorization" header.
 *  2. If absent or not a Bearer token, skip authentication and continue the filter chain
 *     (SecurityConfig decides whether the endpoint requires auth or not).
 *  3. Extract the JWT, parse the user's email from it.
 *  4. If the SecurityContext is not yet populated (no previous authentication),
 *     load UserDetails from the database via UserDetailsServiceImpl.
 *  5. Validate the token (signature + expiry + email match).
 *  6. If valid, create a UsernamePasswordAuthenticationToken and store it in the
 *     SecurityContext so the rest of the request pipeline sees the user as authenticated.
 *  7. Always continue the filter chain regardless of token validity — actual
 *     authorization is enforced by SecurityFilterChain rules downstream.
 *
 * Error strategy: any JWT parsing failure logs a warning and falls through to the
 * filter chain without setting authentication, resulting in a 401 from the security rules.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    /**
     * Paths that should never be intercepted by this filter.
     * These are the exact endpoints that issue or refresh tokens;
     * they don't carry (or need) a valid token in the request.
     */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh"
    );

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Step 1: Read the Authorization header
        final String authHeader = request.getHeader("Authorization");

        // Step 2: If missing or doesn't start with "Bearer ", skip JWT processing.
        // The request still continues — public endpoints are allowed, others return 401.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Step 3: Strip "Bearer " prefix to get the raw JWT string
            final String jwt = authHeader.substring(7);

            // Step 4: Extract the email (subject) from the JWT payload
            final String userEmail = jwtUtil.extractEmail(jwt);

            // Only proceed if we have an email AND the SecurityContext isn't already populated.
            // The second condition prevents re-authenticating on every filter invocation
            // (though OncePerRequestFilter already prevents multiple executions per request).
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Step 4b: Load the full UserDetails from the database to get roles, status, etc.
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                // Step 5: Validate the token — checks signature, expiry, and email match
                if (jwtUtil.isTokenValid(jwt, userEmail)) {

                    // Step 6: Build a fully authenticated token and store it in the SecurityContext.
                    // credentials is null — we don't need the password after initial authentication.
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());

                    // Attach request-level details (IP address, session ID) for audit purposes
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // This is the key step: store auth in the SecurityContext so
                    // downstream code (controllers, @PreAuthorize) can read it
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Catch malformed/expired/tampered tokens without crashing the request.
            // The SecurityContext stays empty, and downstream security rules will
            // return a 401 / 403 as appropriate.
            log.warn("JWT authentication failed: {}", e.getMessage());
        }

        // Step 7: Always continue the filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Tells Spring to skip this filter for Swagger/API-doc paths and the three
     * public auth endpoints. All other paths go through the JWT check above.
     *
     * Note: Swagger paths are excluded here for convenience (no token needed
     * to browse the docs). The auth endpoints are excluded because they are
     * the ones that create tokens in the first place.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs")
                || path.startsWith("/v3/api-docs")
                || PUBLIC_PATHS.contains(path);
    }
}
