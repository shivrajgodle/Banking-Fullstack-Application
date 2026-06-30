package com.banking.service;

import com.banking.dto.response.NotificationResponse;
import com.banking.dto.response.PageResponse;
import com.banking.entity.Notification;
import com.banking.entity.User;
import com.banking.enums.NotificationType;
import com.banking.repository.NotificationRepository;
import com.banking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Persists and manages in-app notifications for users (bell icon / notification centre).
 *
 * Unlike emails (EmailService), these are stored in the database and retrieved
 * by the client via the NotificationController. They provide an in-product inbox
 * for account events, transaction alerts, loan updates, etc.
 *
 * ─── Why @Async + REQUIRES_NEW? ───────────────────────────────────────────────
 * Same reasoning as AuditService:
 *  - @Async: notification INSERT should not delay the caller's HTTP response.
 *  - REQUIRES_NEW: runs in its own transaction to avoid FK race conditions.
 *    When send() is called inside a @Transactional method, the parent transaction
 *    may not have committed yet — if this notification shared that transaction, the
 *    Notification.user foreign key could reference a user_id that doesn't exist yet
 *    in the DB (e.g. immediately after user registration).
 *    Running in REQUIRES_NEW means this INSERT happens after its own begin/commit
 *    cycle, but the parent user row is already visible via the re-fetch inside this method.
 *
 * ─── User re-fetch pattern ────────────────────────────────────────────────────
 * The User entity passed into send() may be a detached or stale object from
 * a different persistence context. Re-fetching by ID within this new @Transactional
 * ensures Hibernate manages the entity cleanly and the FK is valid.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * Convenience overload without a referenceId (used for generic messages
     * where there's no specific entity to link to, e.g. "Welcome!").
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(User user, String title, String message, NotificationType type) {
        send(user, title, message, type, null);
    }

    /**
     * Creates a new in-app notification for the given user.
     *
     * @param user        the recipient (re-fetched inside this transaction for safety)
     * @param title       short title shown in the notification list
     * @param message     full notification body
     * @param type        category of the notification (ACCOUNT, TRANSACTION, LOAN, etc.)
     * @param referenceId optional UUID of the related entity (e.g. transaction ID,
     *                    loan ID) so the client can deep-link to the relevant screen
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(User user, String title, String message, NotificationType type, UUID referenceId) {
        try {
            // Re-fetch the user in THIS transaction to ensure the managed entity is valid
            // and the FK won't violate a constraint if the caller's tx hasn't committed yet
            User managedUser = userRepository.findById(user.getId()).orElse(null);
            if (managedUser == null) {
                log.warn("Notification skipped — user {} not found in DB", user.getId());
                return;
            }
            Notification notification = Notification.builder()
                    .user(managedUser)
                    .title(title)
                    .message(message)
                    .type(type)
                    .referenceId(referenceId)
                    .build();
            notificationRepository.save(notification);
        } catch (Exception e) {
            // Notification failures must not propagate to the caller
            log.error("Failed to save notification for user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Returns paginated notifications for a user, newest first.
     * Used by the frontend notification bell/inbox.
     */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getNotifications(UUID userId, int page, int size) {
        Page<Notification> notifPage = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));

        return PageResponse.<NotificationResponse>builder()
                .content(notifPage.getContent().stream().map(this::toResponse).toList())
                .page(page).size(size)
                .totalElements(notifPage.getTotalElements())
                .totalPages(notifPage.getTotalPages())
                .first(notifPage.isFirst()).last(notifPage.isLast())
                .build();
    }

    /** Returns the count of unread notifications — shown as the badge number on the bell icon. */
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    /**
     * Marks all notifications for the user as read in a single batch UPDATE.
     * Called when the user opens the notification centre.
     */
    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsRead(userId);
    }

    /** Maps a Notification entity to the API response DTO. */
    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .read(n.isRead())
                .referenceId(n.getReferenceId())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
