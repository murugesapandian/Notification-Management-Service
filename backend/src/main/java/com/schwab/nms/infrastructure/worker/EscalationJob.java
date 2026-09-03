package com.schwab.nms.infrastructure.worker;

import com.schwab.nms.config.EscalationProperties;
import com.schwab.nms.domain.model.Notification;
import com.schwab.nms.infrastructure.persistence.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Ambiguous-requirement scenario (docs/scenarios/03-ambiguous-requirements.md).
 *
 * Original ask: "make sure a critical alert doesn't get missed if nobody
 * acknowledges it." Resolved here as: every {@code unacknowledgedCriticalThresholdMinutes}
 * (see {@link EscalationProperties}), find CRITICAL notifications nobody has
 * acknowledged (AcknowledgementService) and escalate each exactly once — a new
 * delivery attempt is queued for the configured escalation recipient/channel and
 * reused through the existing delivery pipeline (DeliveryWorker/DeliveryDispatcher)
 * rather than a separate send path, so escalated messages get the same retry and
 * audit behavior as any other delivery. Escalating twice for the same notification
 * was explicitly ruled out to avoid alert fatigue; a periodic re-page/on-call-
 * rotation integration is future scope, not implemented here.
 *
 * Purely a scheduling trigger: {@link EscalationExecutor} does the actual
 * per-notification transactional work (see that class's javadoc for why the split
 * exists — both the general self-invocation reason shared with DeliveryWorker, and
 * the specific need for one notification's write conflict not to roll back
 * everyone else's in the same poll).
 */
@Component
public class EscalationJob {

    private static final Logger log = LoggerFactory.getLogger(EscalationJob.class);

    private final NotificationRepository notificationRepository;
    private final EscalationExecutor executor;
    private final EscalationProperties properties;
    private final Clock clock;

    public EscalationJob(NotificationRepository notificationRepository,
                          EscalationExecutor executor,
                          EscalationProperties properties,
                          Clock clock) {
        this.notificationRepository = notificationRepository;
        this.executor = executor;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${nms.escalation.poll-interval-ms:30000}")
    public void escalateOverdueCriticalNotifications() {
        Instant cutoff = Instant.now(clock).minus(Duration.ofMinutes(properties.unacknowledgedCriticalThresholdMinutes()));
        List<Notification> overdue = notificationRepository.findEligibleForEscalation(cutoff);

        for (Notification notification : overdue) {
            try {
                executor.escalateOne(notification.getId());
            } catch (ObjectOptimisticLockingFailureException e) {
                // Lost a race against a concurrent write (e.g. an acknowledge landing at the
                // same moment) — the next poll cycle will re-evaluate this notification's
                // eligibility from scratch, so skipping it here is safe, not a lost update.
                log.info("Escalation for notification {} lost a concurrent-update race; will retry next poll",
                        notification.getId());
            } catch (Exception e) {
                log.error("Unhandled error escalating notification {}", notification.getId(), e);
            }
        }
    }
}
