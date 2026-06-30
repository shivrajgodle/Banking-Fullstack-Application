package com.banking.security;

import com.banking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridge between Spring Security and the application's User entity.
 *
 * Spring Security calls loadUserByUsername() in two places:
 *  1. During login — DaoAuthenticationProvider uses it to fetch user details
 *     and then compares the stored BCrypt hash against the supplied password.
 *  2. During JWT validation — JwtAuthenticationFilter calls it after parsing
 *     the email from the token to load the full UserDetails (including roles
 *     and account status flags).
 *
 * The User entity itself implements UserDetails, so it can be returned directly
 * without any intermediate mapping. This means Spring Security automatically
 * reads isEnabled(), isAccountNonLocked(), getAuthorities(), etc. from User.
 *
 * @Transactional(readOnly=true) ensures this runs within a read-only transaction,
 * which allows JPA to skip dirty-checking and use a read-optimized DB connection.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a user by email address (used as the "username" in this application).
     *
     * @param email the email address from the login request or JWT subject
     * @return the User entity wrapped as UserDetails
     * @throws UsernameNotFoundException if no user with that email exists
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
