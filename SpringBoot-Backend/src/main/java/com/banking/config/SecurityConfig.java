package com.banking.config;

import com.banking.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Central Spring Security configuration for the banking application.
 *
 * Responsibilities:
 *  1. Define which endpoints are public vs. require authentication
 *  2. Enforce role-based access for sensitive operations (admin, manager, teller)
 *  3. Plug in the custom JWT filter so every request carries token-based identity
 *  4. Set up stateless session management (no HTTP sessions — JWTs replace them)
 *  5. Configure CORS so frontend apps on different origins can call the API
 *  6. Provide shared beans: PasswordEncoder, AuthenticationProvider, AuthenticationManager
 *
 * Security model overview:
 *  - CUSTOMER  → can access their own accounts, transactions, loans, FDs
 *  - TELLER    → can additionally perform cash deposits
 *  - MANAGER   → can approve/reject loans and change user status/KYC
 *  - ADMIN     → full access including all of the above plus /admin/** endpoints
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // enables @PreAuthorize("hasRole(...)") on individual controller methods
@RequiredArgsConstructor
public class SecurityConfig {

    /** Custom JWT filter that reads the Authorization header and authenticates the request. */
    private final JwtAuthenticationFilter jwtAuthFilter;

    /** Loads user details from the database by email (used by DaoAuthenticationProvider). */
    private final UserDetailsService userDetailsService;

    /**
     * Endpoints that are accessible without any JWT token.
     * Anything not listed here requires a valid Bearer token.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
        "/auth/**",           // register, login, refresh, OTP send
        "/swagger-ui/**",     // Swagger UI static assets
        "/swagger-ui.html",
        "/api-docs/**",       // OpenAPI JSON spec
        "/actuator/health",   // health probe for container orchestration
        "/actuator/info"
    };

    /**
     * Defines the complete HTTP security filter chain.
     *
     * Key decisions:
     *  - CSRF disabled:  REST APIs are stateless; CSRF tokens are only needed for
     *                    cookie/session-based authentication, not JWT.
     *  - STATELESS:      Spring will never create or use an HttpSession — every request
     *                    must carry its own JWT.
     *  - Role hierarchy: requestMatchers rules are evaluated top-to-bottom; the first
     *                    match wins, so more specific paths are listed before anyRequest().
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — safe because we use JWT (not cookies/sessions) for auth
            .csrf(AbstractHttpConfigurer::disable)

            // Apply the CORS policy defined in corsConfigurationSource()
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Never create or use an HTTP session — each request is independently authenticated via JWT
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no token needed
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                // Allow browsers to send pre-flight OPTIONS requests without a token
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Admin-only: user management, system config, audit logs, etc.
                .requestMatchers("/admin/**").hasRole("ADMIN")

                // Manager or Admin: loan approval/rejection and KYC/status changes
                .requestMatchers("/loans/*/approve", "/loans/*/reject").hasAnyRole("MANAGER", "ADMIN")
                .requestMatchers("/users/*/status", "/users/*/kyc").hasAnyRole("MANAGER", "ADMIN")

                // Teller: cash deposit at the counter (branch staff workflow)
                .requestMatchers("/transactions/deposit").hasAnyRole("TELLER", "ADMIN", "MANAGER")

                // Everything else requires any authenticated user (any role)
                .anyRequest().authenticated()
            )

            // Use our DaoAuthenticationProvider (DB lookup + BCrypt password check)
            .authenticationProvider(authenticationProvider())

            // Insert JWT filter BEFORE the standard UsernamePasswordAuthenticationFilter,
            // so the SecurityContext is populated before Spring's own checks run
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Wires the database-backed UserDetailsService with BCrypt password verification.
     * Spring Security calls this provider during login to validate credentials.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes the AuthenticationManager bean so AuthService can programmatically
     * authenticate a username/password during the login flow.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BCrypt with strength 12 — this is the cost factor for the hashing rounds.
     * Higher values = more secure but slower; 12 is a good production default
     * (roughly 250ms per hash, making brute-force attacks very expensive).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * CORS policy: allows any origin/header but requires credentials.
     *
     * In production, replace the wildcard origin pattern with the actual
     * frontend domain(s) for tighter security.
     * maxAge=3600 caches the preflight result for 1 hour in the browser.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*")); // restrict in prod
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // cache preflight for 1 hour
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
