package com.schwab.nms.application;

import com.schwab.nms.domain.enums.AuditAction;
import com.schwab.nms.domain.model.Notification;
import com.schwab.nms.infrastructure.persistence.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Use case behind the ambiguous-requirement scenario (docs/scenarios/03-ambiguous-requirements.md):
 * "acknowledge" is defined here as an explicit action by a human/on-call system —
 * never inferred from a successful delivery — because a message merely reaching an
 * inbox is not evidence anyone has seen or acted on it. Acknowledgement is
 * idempotent and first-writer-wins: repeat calls (e.g. two people on a shared
 * channel both click acknowledge) do not overwrite who acknowledged first, and do
 * not re-fire the audit event.
 */
@Service
public class AcknowledgementService {

    private final NotificationRepository notificationRepository;
    private final AuditService auditService;
    private final Clock clock;

    public AcknowledgementService(NotificationRepository notificationRepository, AuditService auditService, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public Notification acknowledge(UUID notificationId, String acknowledgedBy) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NoSuchElementException("Notification not found: " + notificationId));

        if (notification.getAcknowledgedAt() == null) {
            Instant now = Instant.now(clock);
            notification.setAcknowledgedAt(now);
            notification.setAcknowledgedBy(acknowledgedBy);
            notification.setUpdatedAt(now);
            notificationRepository.save(notification);
            auditService.record(notificationId, AuditAction.NOTIFICATION_ACKNOWLEDGED,
                    "acknowledgedBy=" + acknowledgedBy, acknowledgedBy);
        }
        return notification;
    }
}
