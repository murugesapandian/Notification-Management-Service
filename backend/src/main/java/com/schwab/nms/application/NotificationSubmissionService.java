package com.schwab.nms.application;

import com.schwab.nms.application.command.SubmitNotificationCommand;
import com.schwab.nms.application.event.NotificationSubmittedEvent;
import com.schwab.nms.domain.enums.AuditAction;
import com.schwab.nms.domain.enums.NotificationStatus;
import com.schwab.nms.domain.model.IdempotencyRecord;
import com.schwab.nms.domain.model.Notification;
import com.schwab.nms.domain.model.NotificationRecipient;
import com.schwab.nms.domain.model.RequestedChannel;
import com.schwab.nms.infrastructure.persistence.repository.IdempotencyRecordRepository;
import com.schwab.nms.infrastructure.persistence.repository.NotificationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Use case: accept a notification submission (section 4.1) with request-level
 * idempotency (section 4.4). See docs/scenarios/01-greenfield.md for decomposition.
 */
@Service
public class NotificationSubmissionService {

    /** Retention window for idempotency records; see IdempotencyRecord javadoc. */
    static final Duration IDEMPOTENCY_RETENTION = Duration.ofDays(7);

    private final NotificationRepository notificationRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public NotificationSubmissionService(NotificationRepository notificationRepository,
                                          IdempotencyRecordRepository idempotencyRecordRepository,
                                          AuditService auditService,
                                          ApplicationEventPublisher eventPublisher,
                                          Clock clock) {
        this.notificationRepository = notificationRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public NotificationSubmissionResult submit(SubmitNotificationCommand command) {
        Instant now = Instant.now(clock);

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<IdempotencyRecord> existing = idempotencyRecordRepository
                    .findBySourceSystemAndIdempotencyKey(command.sourceSystem(), command.idempotencyKey());
            if (existing.isPresent() && existing.get().getExpiresAt().isAfter(now)) {
                Notification existingNotification = notificationRepository.findById(existing.get().getNotificationId())
                        .orElseThrow(() -> new NoSuchElementException("Idempotency record points at a missing notification"));
                auditService.record(existingNotification.getId(), AuditAction.DUPLICATE_SUPPRESSED,
                        "idempotencyKey=" + command.idempotencyKey() + " sourceSystem=" + command.sourceSystem());
                return new NotificationSubmissionResult(existingNotification, true);
            }
        }

        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .sourceSystem(command.sourceSystem())
                .eventId(command.eventId())
                .notificationType(command.notificationType())
                .severity(command.severity())
                .priority(command.priority())
                .subject(command.subject())
                .body(command.body())
                .idempotencyKey(command.idempotencyKey())
                .overallStatus(NotificationStatus.RECEIVED)
                .createdAt(now)
                .updatedAt(now)
                .scheduledAt(command.scheduledAt())
                .expiresAt(command.expiresAt())
                .build();

        command.recipients().forEach(r -> notification.addRecipient(NotificationRecipient.builder()
                .recipientId(r.recipientId())
                .recipientType(r.recipientType())
                .build()));

        command.requestedChannels().forEach(c -> notification.addRequestedChannel(RequestedChannel.builder()
                .channel(c)
                .build()));

        notification.setOverallStatus(NotificationStatus.VALIDATED);

        Notification saved;
        try {
            saved = notificationRepository.save(notification);
        } catch (DataIntegrityViolationException e) {
            // Race: two concurrent submissions with the same idempotency key. Re-check and
            // resolve to whichever row won, rather than surfacing a 500 to the caller.
            return resolveConcurrentDuplicate(command, now);
        }

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            idempotencyRecordRepository.save(IdempotencyRecord.builder()
                    .sourceSystem(command.sourceSystem())
                    .idempotencyKey(command.idempotencyKey())
                    .notificationId(saved.getId())
                    .createdAt(now)
                    .expiresAt(now.plus(IDEMPOTENCY_RETENTION))
                    .build());
        }

        auditService.record(saved.getId(), AuditAction.NOTIFICATION_ACCEPTED,
                "sourceSystem=" + command.sourceSystem() + " type=" + command.notificationType()
                        + " severity=" + command.severity());

        eventPublisher.publishEvent(new NotificationSubmittedEvent(saved.getId()));

        return new NotificationSubmissionResult(saved, false);
    }

    private NotificationSubmissionResult resolveConcurrentDuplicate(SubmitNotificationCommand command, Instant now) {
        IdempotencyRecord record = idempotencyRecordRepository
                .findBySourceSystemAndIdempotencyKey(command.sourceSystem(), command.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException("Expected a winning idempotency record after a save conflict"));
        Notification winner = notificationRepository.findById(record.getNotificationId())
                .orElseThrow(() -> new NoSuchElementException("Idempotency record points at a missing notification"));
        auditService.record(winner.getId(), AuditAction.DUPLICATE_SUPPRESSED,
                "concurrent-race idempotencyKey=" + command.idempotencyKey());
        return new NotificationSubmissionResult(winner, true);
    }
}
