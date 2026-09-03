package com.schwab.nms.application.event;

import com.schwab.nms.application.DeliveryOrchestrationService;
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
 */
@Component
public class NotificationSubmittedEventListener {

    private final DeliveryOrchestrationService orchestrationService;

    public NotificationSubmittedEventListener(DeliveryOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationSubmitted(NotificationSubmittedEvent event) {
        orchestrationService.orchestrate(event.notificationId());
    }
}
