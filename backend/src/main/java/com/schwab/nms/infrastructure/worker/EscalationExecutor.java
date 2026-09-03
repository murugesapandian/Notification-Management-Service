package com.schwab.nms.infrastructure.worker;

import com.schwab.nms.application.AuditService;
import com.schwab.nms.config.EscalationProperties;
import com.schwab.nms.domain.enums.AuditAction;
import com.schwab.nms.domain.enums.DeliveryStatus;
import com.schwab.nms.domain.enums.NotificationStatus;
import com.schwab.nms.domain.enums.RecipientType;
import com.schwab.nms.domain.model.DeliveryAttempt;
import com.schwab.nms.domain.model.Notification;
import com.schwab.nms.infrastructure.persistence.repository.DeliveryAttemptRepository;
import com.schwab.nms.infrastructure.persistence.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Transactional collaborator behind {@link EscalationJob} — separated out, like
 * DeliveryDispatcher, both so {@code @Transactional} is actually intercepted (see
 * that class's javadoc) and so each notification gets its <em>own</em> transaction.
 * With a single batch-wide transaction, one notification losing an optimistic-lock
 * race against a concurrent write (e.g. someone acknowledging it right as this job
 * runs) would roll back every other notification's escalation in the same batch
 * too. Per-notification transactions mean a single conflict only costs that one
 * notification, which naturally retries on the job's next poll.
 */
@Service
public class EscalationExecutor {

    private static final Logger log = LoggerFactory.getLogger(EscalationExecutor.class);

    private final NotificationRepository notificationRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final AuditService auditService;
    private final EscalationProperties properties;
    private final Clock clock;

    public EscalationExecutor(NotificationRepository notificationRepository,
                               DeliveryAttemptRepository deliveryAttemptRepository,
                               AuditService auditService,
                               EscalationProperties properties,
                               Clock clock) {
        this.notificationRepository = notificationRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void escalateOne(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NoSuchElementException("Notification not found: " + notificationId));

        // Re-check eligibility against the freshly loaded row: it may have been
        // acknowledged or already escalated since the batch query ran.
        if (notification.getAcknowledgedAt() != null || notification.getEscalatedAt() != null) {
            return;
        }

        Instant now = Instant.now(clock);
        DeliveryAttempt escalationAttempt = DeliveryAttempt.builder()
                .notificationId(notification.getId())
                .recipientId(properties.escalationRecipientId())
                .recipientType(RecipientType.GROUP)
                .channel(properties.escalationChannel())
                .status(DeliveryStatus.PENDING)
                .attemptCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            deliveryAttemptRepository.save(escalationAttempt);
        } catch (DataIntegrityViolationException e) {
            log.warn("Escalation delivery unit already existed for notification {}", notification.getId());
        }

        notification.setEscalatedAt(now);
        notification.setOverallStatus(NotificationStatus.ESCALATED);
        notification.setUpdatedAt(now);
        notificationRepository.save(notification);

        auditService.record(notification.getId(), AuditAction.NOTIFICATION_ESCALATED,
                "recipient=" + properties.escalationRecipientId() + " channel=" + properties.escalationChannel());
    }
}
