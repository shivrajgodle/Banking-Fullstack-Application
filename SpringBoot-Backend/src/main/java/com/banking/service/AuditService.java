package com.banking.service;

import com.banking.entity.AuditLog;
import com.banking.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records an immutable audit trail of every significant user action in the system.
 *
 * Every write operation in the application (account creation, deposits, transfers,
 * login, logout, loan actions, etc.) calls one of the log() overloads here.
 *
 * ─── Why @Async + REQUIRES_NEW? ───────────────────────────────────────────────
 *
 *  @Async:
 *    Audit logging is a cross-cutting concern that should not add latency to the
 *    main business operation. Running on the async thread pool means the caller
 *    returns to the client immediately without waiting for the DB INSERT.
 *
 *  Propagation.REQUIRES_NEW:
 *    Suspends the caller's transaction and starts a brand-new one.
 *    Two important consequences:
 *
 *    1. Audit records persist even if the caller rolls back.
 *       Example: A failed transfer should still be logged with status="FAILURE".
 *       If audit shared the caller's transaction, the log entry would disappear
 *       on rollback along with the failed transfer.
 *
 *    2. The audit INSERT is committed independently, so it never holds locks
 *       on the caller's rows or delays the caller's commit.
 *
 * ─── Failure handling ─────────────────────────────────────────────────────────
 * Audit failures are caught and logged — an audit DB error must not crash the
 * caller or cause a 500 response. The trade-off is that some audit entries
 * may be missing in extreme DB failure scenarios.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Full-detail audit log entry.
     *
     * @param userId     the user who performed the action
     * @param action     uppercase action name (e.g. "USER_LOGIN", "TRANSFER", "ACCOUNT_FROZEN")
     * @param entityType the type of entity affected (e.g. "User", "Account", "Transaction")
     * @param entityId   the UUID (as string) of the affected entity
     * @param oldValues  JSON snapshot of the entity before the change (nullable)
     * @param newValues  JSON snapshot of the entity after the change (nullable)
     * @param ipAddress  the client IP address (nullable; useful for fraud detection)
     * @param status     "SUCCESS" or "FAILURE"
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId, String action, String entityType, String entityId,
                    String oldValues, String newValues, String ipAddress, String status) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .oldValues(oldValues)
                    .newValues(newValues)
                    .ipAddress(ipAddress)
                    .status(status)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Never let audit failures propagate — they must not disrupt the main operation
            log.error("Failed to save audit log for action={} userId={}: {}",
                    action, userId, e.getMessage());
        }
    }

    /**
     * Convenience overload for simple success events where old/new values,
     * IP address, and explicit status are not needed.
     * Defaults status to "SUCCESS" and leaves change fields null.
     *
     * This is the overload used by most service methods.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId, String action, String entityType, String entityId) {
        log(userId, action, entityType, entityId, null, null, null, "SUCCESS");
    }
}
