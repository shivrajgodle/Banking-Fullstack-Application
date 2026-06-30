package com.banking.util;

import com.banking.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Utility for creating, signing, and validating JSON Web Tokens (JWTs).
 *
 * The application uses two types of tokens:
 *
 *  ACCESS TOKEN
 *    - Short-lived (configured via jwt.expiration, e.g. 15 minutes)
 *    - Carries the user's email (subject), role, userId, and full name as claims
 *    - Sent in every API request via "Authorization: Bearer <token>"
 *
 *  REFRESH TOKEN
 *    - Long-lived (configured via jwt.refresh-expiration, e.g. 7 days)
 *    - Contains only the subject (email), no role claims
 *    - Stored in the DB (RefreshToken entity) and used to issue a new access token
 *      when the old one expires, without asking the user to log in again
 *    - On refresh, the old refresh token is revoked and a new one is issued (rotation)
 *
 * All tokens are HMAC-SHA signed using a Base64-encoded secret key
 * injected from application.yml (jwt.secret).
 */
@Component
@Slf4j
public class JwtUtil {

    /** Base64-encoded HMAC secret key. Must be at least 256 bits (32 bytes) for HS256. */
    @Value("${jwt.secret}")
    private String jwtSecret;

    /** Access token validity in milliseconds (e.g. 900000 = 15 minutes). */
    @Value("${jwt.expiration}")
    private long jwtExpiration;

    /** Refresh token validity in milliseconds (e.g. 604800000 = 7 days). */
    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    /**
     * Generates a signed access token for the given user.
     *
     * Custom claims embedded in the token payload:
     *  - "role"   : user's role string (e.g. "CUSTOMER", "ADMIN") — used for
     *               quick role checks without hitting the database
     *  - "userId" : UUID as a string — convenient for downstream services
     *  - "name"   : full name — can be displayed in the frontend without an extra API call
     *
     * @param user the authenticated user
     * @return signed JWT string
     */
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("userId", user.getId().toString());
        claims.put("name", user.getFullName());
        return buildToken(claims, user.getEmail(), jwtExpiration);
    }

    /**
     * Generates a refresh token — contains only the subject (email), no extra claims.
     * Keeping refresh tokens minimal reduces the risk of sensitive data exposure.
     */
    public String generateRefreshToken(User user) {
        return buildToken(new HashMap<>(), user.getEmail(), refreshExpiration);
    }

    /**
     * Core JWT builder shared by both token types.
     *
     * @param extraClaims  additional key-value pairs to embed in the payload
     * @param subject      the "sub" claim — the user's email
     * @param expiration   token lifetime in milliseconds from now
     * @return compacted, Base64url-encoded JWT string
     */
    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)                                           // identifies the user
                .issuedAt(new Date(System.currentTimeMillis()))             // "iat" claim
                .expiration(new Date(System.currentTimeMillis() + expiration)) // "exp" claim
                .id(UUID.randomUUID().toString())                           // unique token ID — enables revocation
                .signWith(getSigningKey())                                  // HMAC-SHA signing
                .compact();                                                 // serialize to "xxxxx.yyyyy.zzzzz"
    }

    /**
     * Validates a token by checking:
     *  1. The subject (email) matches the expected user
     *  2. The token has not expired
     *  3. The signature is valid (verified implicitly by extractAllClaims)
     *
     * @return true if the token is valid and belongs to the given email
     */
    public boolean isTokenValid(String token, String email) {
        try {
            final String username = extractEmail(token);
            return username.equals(email) && !isTokenExpired(token);
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /** Extracts the subject claim (user email) from the token. */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /** Extracts the custom "userId" claim (UUID as string). */
    public String extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", String.class));
    }

    /** Extracts the custom "role" claim (e.g. "ADMIN"). */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /** Extracts the expiration date from the token. */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Generic claim extractor — parses the token once and applies the resolver function.
     * All public extract* methods delegate here to avoid parsing the token multiple times.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Parses and verifies the JWT signature, returning the full claims payload.
     * Throws a JwtException if the token is tampered with, expired, or malformed.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())   // verify the HMAC signature
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Returns true if the token's expiration time is in the past. */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Decodes the Base64 secret from config and creates an HMAC-SHA key.
     * Keys.hmacShaKeyFor automatically selects the correct algorithm
     * strength (HS256/384/512) based on key length.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** Returns the access token expiry duration (milliseconds) — used in AuthResponse. */
    public long getExpirationTime() {
        return jwtExpiration;
    }
}
