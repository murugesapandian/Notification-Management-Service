package com.schwab.nms.infrastructure.worker;

import com.schwab.nms.application.AuditService;
import com.schwab.nms.application.NotificationStatusAggregator;
import com.schwab.nms.application.RetryPolicy;
import com.schwab.nms.domain.enums.AuditAction;
import com.schwab.nms.domain.enums.Channel;
import com.schwab.nms.domain.enums.DeliveryStatus;
import com.schwab.nms.domain.model.DeliveryAttempt;
import com.schwab.nms.infrastructure.persistence.repository.DeliveryAttemptRepository;
import com.schwab.nms.infrastructure.provider.ChannelProvider;
import com.schwab.nms.infrastructure.provider.DeliveryContext;
import com.schwab.nms.infrastructure.provider.EmailChannelProvider;
import com.schwab.nms.infrastructure.provider.ProviderResult;
import com.schwab.nms.infrastructure.provider.PushChannelProvider;
import com.schwab.nms.infrastructure.provider.SmsChannelProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Transactional collaborator behind {@link DeliveryWorker}. Split into its own
 * Spring bean deliberately: {@code @Transactional} only takes effect on calls that
 * go through the bean's proxy, and a scheduled method calling {@code this.foo()}
 * on itself bypasses that proxy entirely (a real bug caught by
 * {@code NotificationApiIT} during this build — see docs/testing-strategy.md
 * "Findings from validation"). Keeping the transactional methods on a separate,
 * externally-invoked bean makes the proxying correct by construction.
 *
 * The two-phase claim/dispatch split is also what makes reprocessing safe
 * (section 4.4): {@link #claimDueBatch()} takes the pessimistic lock and flips
 * each attempt to IN_PROGRESS <em>in the same transaction</em>, so the lock's
 * effect (excluding other workers) is durable in the row's status even after the
 * lock itself is released at commit — a second poller can no longer select the
 * same row because it no longer matches the PENDING/RETRY_SCHEDULED predicate.
 * {@link #dispatch(UUID)} then re-checks the status before sending, so calling it
 * twice for the same id (e.g. a crashed worker retried by the caller) is a no-op
 * the second time.
 */
@Service
public class DeliveryDispatcher {

    private static final int BATCH_SIZE = 20;

    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final EmailChannelProvider emailChannelProvider;
    private final SmsChannelProvider smsChannelProvider;
    private final PushChannelProvider pushChannelProvider;
    private final RetryPolicy retryPolicy;
    private final AuditService auditService;
    private final NotificationStatusAggregator statusAggregator;
    private final Clock clock;

    public DeliveryDispatcher(DeliveryAttemptRepository deliveryAttemptRepository,
                               EmailChannelProvider emailChannelProvider,
                               SmsChannelProvider smsChannelProvider,
                               PushChannelProvider pushChannelProvider,
                               RetryPolicy retryPolicy,
                               AuditService auditService,
                               NotificationStatusAggregator statusAggregator,
                               Clock clock) {
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.emailChannelProvider = emailChannelProvider;
        this.smsChannelProvider = smsChannelProvider;
        this.pushChannelProvider = pushChannelProvider;
        this.retryPolicy = retryPolicy;
        this.auditService = auditService;
        this.statusAggregator = statusAggregator;
        this.clock = clock;
    }

    @Transactional
    public List<UUID> claimDueBatch() {
        Instant now = Instant.now(clock);
        List<DeliveryAttempt> due = deliveryAttemptRepository.lockNextBatchDue(now, PageRequest.of(0, BATCH_SIZE));

        List<UUID> claimed = new ArrayList<>();
        for (DeliveryAttempt attempt : due) {
            attempt.setStatus(DeliveryStatus.IN_PROGRESS);
            attempt.setAttemptCount(attempt.getAttemptCount() + 1);
            attempt.setUpdatedAt(now);
            deliveryAttemptRepository.save(attempt);

            auditService.record(attempt.getNotificationId(), AuditAction.DELIVERY_ATTEMPTED,
                    "channel=" + attempt.getChannel() + " attempt=" + attempt.getAttemptCount());
            claimed.add(attempt.getId());
        }
        return claimed;
    }

    @Transactional
    public void dispatch(UUID attemptId) {
        DeliveryAttempt attempt = deliveryAttemptRepository.findById(attemptId).orElse(null);
        if (attempt == null || attempt.getStatus() != DeliveryStatus.IN_PROGRESS) {
            // Already handled by a prior/concurrent call for this id, or the row no longer exists — no-op.
            return;
        }

        ChannelProvider provider = resolveProvider(attempt.getChannel());
        DeliveryContext context = new DeliveryContext(
                attempt.getNotificationId(), attempt.getId(), attempt.getRecipientId(),
                attempt.getRecipientType(), null, null, attempt.getAttemptCount());

        ProviderResult result = provider.send(context);
        applyResult(attempt, result);

        deliveryAttemptRepository.save(attempt);
        statusAggregator.refresh(attempt.getNotificationId());
    }

    private void applyResult(DeliveryAttempt attempt, ProviderResult result) {
        Instant now = Instant.now(clock);
        attempt.setUpdatedAt(now);

        if (result.success()) {
            attempt.setStatus(DeliveryStatus.SUCCESS);
            attempt.setProviderMessageId(result.providerMessageId());
            attempt.setLastFailureCategory(null);
            attempt.setLastErrorMessage(null);
            attempt.setNextRetryAt(null);
            auditService.record(attempt.getNotificationId(), AuditAction.DELIVERY_SUCCEEDED,
                    "channel=" + attempt.getChannel() + " providerMessageId=" + result.providerMessageId());
            return;
        }

        attempt.setLastFailureCategory(result.failureCategory());
        attempt.setLastErrorMessage(result.errorMessage());
        auditService.record(attempt.getNotificationId(), AuditAction.DELIVERY_FAILED,
                "channel=" + attempt.getChannel() + " category=" + result.failureCategory()
                        + " attempt=" + attempt.getAttemptCount());

        if (retryPolicy.shouldRetry(result.failureCategory(), attempt.getAttemptCount(), attempt.getMaxAttempts())) {
            attempt.setStatus(DeliveryStatus.RETRY_SCHEDULED);
            attempt.setNextRetryAt(retryPolicy.nextRetryAt(now, attempt.getAttemptCount(), result.retryAfterSeconds()));
            auditService.record(attempt.getNotificationId(), AuditAction.RETRY_SCHEDULED,
                    "channel=" + attempt.getChannel() + " nextRetryAt=" + attempt.getNextRetryAt());
        } else {
            attempt.setStatus(DeliveryStatus.ABANDONED);
            attempt.setNextRetryAt(null);
            auditService.record(attempt.getNotificationId(), AuditAction.DELIVERY_ABANDONED,
                    "channel=" + attempt.getChannel() + " category=" + result.failureCategory());
        }
    }

    private ChannelProvider resolveProvider(Channel channel) {
        if (channel == Channel.EMAIL) {
            return emailChannelProvider;
        } else if (channel == Channel.SMS) {
            return smsChannelProvider;
        } else if (channel == Channel.PUSH) {
            return pushChannelProvider;
        }
        throw new IllegalStateException("No provider configured for channel " + channel);
    }
}
