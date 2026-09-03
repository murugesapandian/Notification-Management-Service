package com.schwab.nms.application;

import com.schwab.nms.domain.enums.AuditAction;
import com.schwab.nms.domain.enums.DeliveryStatus;
import com.schwab.nms.domain.enums.NotificationStatus;
import com.schwab.nms.domain.model.DeliveryAttempt;
import com.schwab.nms.domain.model.Notification;
import com.schwab.nms.domain.model.RoutingDecision;
import com.schwab.nms.infrastructure.persistence.repository.DeliveryAttemptRepository;
import com.schwab.nms.infrastructure.persistence.repository.NotificationRepository;
import com.schwab.nms.infrastructure.persistence.repository.RoutingDecisionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Turns an accepted notification into queued delivery work: resolves channels
 * (RoutingService), records the routing decision as audit, and creates one
 * {@link DeliveryAttempt} per (recipient, channel) unit for the delivery worker to
 * pick up. Invoked asynchronously, after the submission transaction commits, by
 * {@link com.schwab.nms.application.event.NotificationSubmittedEventListener} — kept as a
 * separate bean rather than a self-call so {@code @Transactional} here is actually
 * intercepted by Spring's proxy (see that listener's javadoc, and
 * docs/testing-strategy.md "Findings from validation" for the bug this avoids).
 */
@Service
public class DeliveryOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryOrchestrationService.class);

    private final NotificationRepository notificationRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final RoutingDecisionRepository routingDecisionRepository;
    private final RoutingService routingService;
    private final AuditService auditService;
    private final Clock clock;

    public DeliveryOrchestrationService(NotificationRepository notificationRepository,
                                         DeliveryAttemptRepository deliveryAttemptRepository,
                                         RoutingDecisionRepository routingDecisionRepository,
                                         RoutingService routingService,
                                         AuditService auditService,
                                         Clock clock) {
        this.notificationRepository = notificationRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.routingDecisionRepository = routingDecisionRepository;
        this.routingService = routingService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public void orchestrate(java.util.UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NoSuchElementException("Notification not found: " + notificationId));

        if (notification.getExpiresAt() != null && notification.getExpiresAt().isBefore(Instant.now(clock))) {
            notification.setOverallStatus(NotificationStatus.EXPIRED);
            notification.setUpdatedAt(Instant.now(clock));
            notificationRepository.save(notification);
            auditService.record(notificationId, AuditAction.NOTIFICATION_EXPIRED, "expired before routing");
            return;
        }

        var decisions = routingService.resolve(notification);
        Instant now = Instant.now(clock);

        for (RoutingService.RoutingResult decision : decisions) {
            routingDecisionRepository.save(RoutingDecision.builder()
                    .notificationId(notificationId)
                    .recipientId(decision.recipientId())
                    .requestedChannel(decision.requestedChannel())
                    .resolvedChannel(decision.resolvedChannel())
                    .reason(decision.reason())
                    .decidedAt(now)
                    .build());

            DeliveryAttempt attempt = DeliveryAttempt.builder()
                    .notificationId(notificationId)
                    .recipientId(decision.recipientId())
                    .recipientType(decision.recipientType())
                    .channel(decision.resolvedChannel())
                    .status(DeliveryStatus.PENDING)
                    .attemptCount(0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            try {
                deliveryAttemptRepository.save(attempt);
            } catch (DataIntegrityViolationException e) {
                // Duplicate (notification_id, recipient_id, channel) unit — the event fired
                // more than once for the same notification. Idempotent no-op: the original
                // attempt row already represents this unit of work.
                log.warn("Duplicate delivery unit suppressed notificationId={} recipient={} channel={}",
                        notificationId, decision.recipientId(), decision.resolvedChannel());
            }
        }

        auditService.record(notificationId, AuditAction.ROUTING_DECISION_MADE,
                "resolvedUnits=" + decisions.size());
        auditService.record(notificationId, AuditAction.DELIVERY_QUEUED,
                "queuedUnits=" + decisions.size());

        notification.setOverallStatus(NotificationStatus.QUEUED);
        notification.setUpdatedAt(now);
        notificationRepository.save(notification);
    }
}
