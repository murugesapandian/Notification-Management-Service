package com.schwab.nms.application;

import com.schwab.nms.domain.enums.DeliveryStatus;
import com.schwab.nms.domain.enums.NotificationStatus;
import com.schwab.nms.domain.model.DeliveryAttempt;
import com.schwab.nms.domain.model.Notification;
import com.schwab.nms.infrastructure.persistence.repository.DeliveryAttemptRepository;
import com.schwab.nms.infrastructure.persistence.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Recomputes a notification's overall status from the terminal/non-terminal state
 * of its delivery attempts (section 4.2). Called by {@link com.schwab.nms.infrastructure.worker.DeliveryWorker}
 * after every attempt outcome.
 */
@Service
public class NotificationStatusAggregator {

    private final NotificationRepository notificationRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final Clock clock;

    public NotificationStatusAggregator(NotificationRepository notificationRepository,
                                         DeliveryAttemptRepository deliveryAttemptRepository,
                                         Clock clock) {
        this.notificationRepository = notificationRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.clock = clock;
    }

    @Transactional
    public void refresh(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NoSuchElementException("Notification not found: " + notificationId));

        // Escalated/expired/rejected/duplicate-suppressed are terminal and not recomputed here.
        // ESCALATED in particular must not be overwritten once EscalationJob sets it: dispatching
        // the escalation's own DeliveryAttempt through the normal pipeline would otherwise flip the
        // notification straight back to DELIVERED/PARTIALLY_DELIVERED and erase the "this needed
        // human escalation" signal (docs/scenarios/03-ambiguous-requirements.md).
        if (notification.getOverallStatus() == NotificationStatus.EXPIRED
                || notification.getOverallStatus() == NotificationStatus.REJECTED
                || notification.getOverallStatus() == NotificationStatus.ESCALATED) {
            return;
        }

        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByNotificationId(notificationId);
        if (attempts.isEmpty()) {
            return;
        }

        boolean anyNonTerminal = attempts.stream().anyMatch(a -> !a.isTerminal());
        if (anyNonTerminal) {
            setStatus(notification, NotificationStatus.IN_PROGRESS);
            return;
        }

        long successCount = attempts.stream().filter(a -> a.getStatus() == DeliveryStatus.SUCCESS).count();
        if (successCount == attempts.size()) {
            setStatus(notification, NotificationStatus.DELIVERED);
        } else if (successCount > 0) {
            setStatus(notification, NotificationStatus.PARTIALLY_DELIVERED);
        } else {
            setStatus(notification, NotificationStatus.FAILED);
        }
    }

    private void setStatus(Notification notification, NotificationStatus status) {
        if (notification.getOverallStatus() != status) {
            notification.setOverallStatus(status);
            notification.setUpdatedAt(Instant.now(clock));
            notificationRepository.save(notification);
        }
    }
}
