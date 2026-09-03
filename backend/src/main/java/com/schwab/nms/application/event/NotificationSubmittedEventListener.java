package com.schwab.nms.application.event;

import com.schwab.nms.application.DeliveryOrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Async trigger for post-submission routing/queueing (section 4.1 "Asynchronous
 * processing"). Deliberately thin: it only forwards to
 * {@link DeliveryOrchestrationService#orchestrate}, an external bean call, rather
 * than doing the work itself. A {@code @Transactional} method can only be
 * intercepted by Spring's proxy when called from <em>outside</em> the declaring
 * bean — calling {@code this.orchestrate(...)} from within the same class silently
 * runs with no transaction at all. That exact mistake shipped in an earlier
 * iteration of this listener and was caught by NotificationApiIT
 * (see docs/testing-strategy.md "Findings from validation"); this two-bean split
 * is the fix, not a stylistic preference.
 *
 * A second, related finding from the same test run: for a CRITICAL notification
 * with a very short (or zero, as in tests) escalation threshold, EscalationJob can
 * pick up the notification and write to it before this listener's own first write
 * lands — both start from the same freshly-committed row. A small retry here
 * absorbs that race the same way the acknowledge API boundary does (see
 * NotificationController); see docs/testing-strategy.md and
 * docs/scenarios/03-ambiguous-requirements.md for the full account, including why
 * a single mutable Notification aggregate row is a known contention point at
 * higher scale.
 */
@Component
public class NotificationSubmittedEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationSubmittedEventListener.class);
    private static final int MAX_ATTEMPTS = 3;

    private final DeliveryOrchestrationService orchestrationService;

    public NotificationSubmittedEventListener(DeliveryOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationSubmitted(NotificationSubmittedEvent event) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                orchestrationService.orchestrate(event.notificationId());
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("Routing orchestration for notification {} lost a concurrent-update race (attempt {}/{})",
                        event.notificationId(), attempt, MAX_ATTEMPTS);
            }
        }
        log.error("Routing orchestration for notification {} did not succeed after {} attempts; "
                        + "it will remain unrouted unless escalation or a manual retry picks it up",
                event.notificationId(), MAX_ATTEMPTS);
    }
}
