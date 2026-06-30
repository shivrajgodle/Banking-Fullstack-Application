package com.banking.util;

import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates unique, human-readable reference numbers for accounts, loans, FDs, and transactions.
 *
 * Format examples:
 *  - Account : ACC26000001 (ACC + 2-digit year + 8-digit sequence)
 *  - Loan    : LN26000002
 *  - FD      : FD26000003
 *  - TxnRef  : TXN17439218245691 (TXN + current epoch millis + random 0-999)
 *
 * AtomicLong counter:
 *  - AtomicLong ensures thread-safe increment without synchronization overhead.
 *  - Starting at 100000 means the first sequence number printed is always 100001,
 *    guaranteeing 6+ significant digits from day one.
 *  - NOTE: The counter resets to 100000 on every application restart, so callers
 *    must verify uniqueness in the database (AccountService and others do a
 *    "do-while + existsByAccountNumber" loop before using the generated value).
 *
 * Transaction references use System.currentTimeMillis() + a random suffix to avoid
 * collisions in high-throughput scenarios where multiple transactions happen in the same millisecond.
 */
@Component
public class AccountNumberGenerator {

    /** Shared counter across all generator methods to guarantee uniqueness within one JVM run. */
    private static final AtomicLong counter = new AtomicLong(100000L);

    /**
     * Generates a bank account number.
     * Format: ACC{YY}{XXXXXXXX} — e.g. ACC2600100001
     */
    public String generate() {
        int year = LocalDate.now().getYear() % 100; // last 2 digits of year
        long seq = counter.incrementAndGet();
        return String.format("ACC%02d%08d", year, seq);
    }

    /**
     * Generates a unique loan number.
     * Format: LN{YY}{XXXXXXXX} — e.g. LN2600100002
     */
    public String generateLoanNumber() {
        int year = LocalDate.now().getYear() % 100;
        long seq = counter.incrementAndGet();
        return String.format("LN%02d%08d", year, seq);
    }

    /**
     * Generates a unique Fixed Deposit number.
     * Format: FD{YY}{XXXXXXXX} — e.g. FD2600100003
     */
    public String generateFdNumber() {
        int year = LocalDate.now().getYear() % 100;
        long seq = counter.incrementAndGet();
        return String.format("FD%02d%08d", year, seq);
    }

    /**
     * Generates a short-lived unique transaction reference.
     * Uses epoch milliseconds + a small random suffix to handle burst scenarios.
     * Format: TXN{millis}{0-999} — e.g. TXN1743921824569542
     */
    public String generateTransactionRef() {
        return "TXN" + System.currentTimeMillis() + (int)(Math.random() * 1000);
    }
}
