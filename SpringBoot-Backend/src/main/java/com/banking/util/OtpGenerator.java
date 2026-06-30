package com.banking.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.SecureRandom;

/**
 * Generates cryptographically secure numeric One-Time Passwords (OTPs).
 *
 * SecureRandom is used instead of java.util.Random because:
 *  - Random uses a predictable seed — an attacker who knows one output can guess others
 *  - SecureRandom draws entropy from the OS's cryptographic RNG, making it unpredictable
 *
 * The OTP is zero-padded (e.g. "004291") so it always has exactly otpLength digits.
 * This prevents trivial enumeration by keeping the search space consistent.
 *
 * Default length is 6 digits (10^6 = 1,000,000 possible codes), configurable via
 * banking.otp.length in application.yml.
 */
@Component
public class OtpGenerator {

    /** Single shared instance — SecureRandom is thread-safe and expensive to create. */
    private static final SecureRandom random = new SecureRandom();

    /** How many digits the OTP should have. Defaults to 6 if not set in config. */
    @Value("${banking.otp.length:6}")
    private int otpLength;

    /**
     * Generates a random numeric OTP of the configured length.
     *
     * Example (length=6): random.nextInt(1000000) -> 4291 -> "004291"
     *
     * @return zero-padded numeric OTP string
     */
    public String generate() {
        // bound = 10^otpLength — ensures result is within the right digit range
        int bound = (int) Math.pow(10, otpLength);
        return String.format("%0" + otpLength + "d", random.nextInt(bound));
    }
}
