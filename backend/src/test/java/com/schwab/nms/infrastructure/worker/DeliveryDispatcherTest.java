package com.schwab.nms.infrastructure.worker;

import com.schwab.nms.application.AuditService;
import com.schwab.nms.application.NotificationStatusAggregator;
import com.schwab.nms.application.RetryPolicy;
import com.schwab.nms.domain.enums.Channel;
import com.schwab.nms.domain.enums.DeliveryStatus;
import com.schwab.nms.domain.enums.FailureCategory;
import com.schwab.nms.domain.enums.RecipientType;
import com.schwab.nms.domain.model.DeliveryAttempt;
import com.schwab.nms.infrastructure.persistence.repository.DeliveryAttemptRepository;
import com.schwab.nms.infrastructure.provider.ChannelProviderRegistry;
import com.schwab.nms.infrastructure.provider.EmailChannelProvider;
import com.schwab.nms.infrastructure.provider.PushChannelProvider;
import com.schwab.nms.infrastructure.provider.SlackChannelProvider;
import com.schwab.nms.infrastructure.provider.SmsChannelProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryDispatcherTest {

    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationStatusAggregator statusAggregator;

    private DeliveryDispatcher dispatcher;
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(now, ZoneOffset.UTC);
        ChannelProviderRegistry registry = new ChannelProviderRegistry(List.of(
                new EmailChannelProvider(), new SmsChannelProvider(), new PushChannelProvider(),
                new SlackChannelProvider()));
        dispatcher = new DeliveryDispatcher(deliveryAttemptRepository, registry,
                new RetryPolicy(30, 900), auditService, statusAggregator, fixed);
        lenient().when(deliveryAttemptRepository.save(any(DeliveryAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private DeliveryAttempt pending(String recipientId, Channel channel, int attemptCount) {
        return DeliveryAttempt.builder()
                .id(UUID.randomUUID()).notificationId(UUID.randomUUID())
                .recipientId(recipientId).recipientType(RecipientType.USER_ID)
                .channel(channel).status(DeliveryStatus.PENDING)
                .attemptCount(attemptCount).maxAttempts(5)
                .createdAt(now).updatedAt(now).build();
    }

    private DeliveryAttempt claimed(String recipientId, Channel channel, int attemptCount) {
        DeliveryAttempt a = pending(recipientId, channel, attemptCount);
        a.setStatus(DeliveryStatus.IN_PROGRESS);
        return a;
    }

    @Test
    void claimDueBatchLocksMarksInProgressAndIncrementsAttemptCount() {
        DeliveryAttempt a = pending("user-1", Channel.EMAIL, 0);
        when(deliveryAttemptRepository.lockNextBatchDue(any(), any())).thenReturn(List.of(a));

        List<UUID> claimedIds = dispatcher.claimDueBatch();

        assertThat(claimedIds).containsExactly(a.getId());
        assertThat(a.getStatus()).isEqualTo(DeliveryStatus.IN_PROGRESS);
        assertThat(a.getAttemptCount()).isEqualTo(1);
        assertThat(a.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void claimDueBatchReturnsEmptyWhenNothingIsDue() {
        when(deliveryAttemptRepository.lockNextBatchDue(any(), any())).thenReturn(List.of());

        assertThat(dispatcher.claimDueBatch()).isEmpty();
    }

    @Test
    void dispatchMarksSuccessfulDeliveryAsSuccess() {
        DeliveryAttempt a = claimed("user-1", Channel.EMAIL, 1);
        when(deliveryAttemptRepository.findById(a.getId())).thenReturn(Optional.of(a));

        dispatcher.dispatch(a.getId());

        assertThat(a.getStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(a.getProviderMessageId()).startsWith("email-");
    }

    @Test
    void dispatchSchedulesRetryForTransientFailureUnderMaxAttempts() {
        DeliveryAttempt a = claimed("timeout-user", Channel.EMAIL, 1);
        when(deliveryAttemptRepository.findById(a.getId())).thenReturn(Optional.of(a));

        dispatcher.dispatch(a.getId());

        assertThat(a.getStatus()).isEqualTo(DeliveryStatus.RETRY_SCHEDULED);
        assertThat(a.getLastFailureCategory()).isEqualTo(FailureCategory.TIMEOUT);
        assertThat(a.getNextRetryAt()).isAfter(now);
    }

    @Test
    void dispatchAbandonsImmediatelyOnInvalidRecipientWithoutRetrying() {
        DeliveryAttempt a = claimed("invalid-user", Channel.EMAIL, 1);
        when(deliveryAttemptRepository.findById(a.getId())).thenReturn(Optional.of(a));

        dispatcher.dispatch(a.getId());

        assertThat(a.getStatus()).isEqualTo(DeliveryStatus.ABANDONED);
        assertThat(a.getLastFailureCategory()).isEqualTo(FailureCategory.INVALID_RECIPIENT);
    }

    @Test
    void dispatchAbandonsOnceMaxAttemptsExhaustedEvenForRetryableFailure() {
        DeliveryAttempt a = claimed("timeout-user", Channel.EMAIL, 5);
        a.setMaxAttempts(5);
        when(deliveryAttemptRepository.findById(a.getId())).thenReturn(Optional.of(a));

        dispatcher.dispatch(a.getId());

        assertThat(a.getStatus()).isEqualTo(DeliveryStatus.ABANDONED);
    }

    @Test
    void dispatchRecoversOnSecondAttemptForFlakyRecipient() {
        DeliveryAttempt a = claimed("flaky-user", Channel.SMS, 2);
        when(deliveryAttemptRepository.findById(a.getId())).thenReturn(Optional.of(a));

        dispatcher.dispatch(a.getId());

        assertThat(a.getStatus()).isEqualTo(DeliveryStatus.SUCCESS);
    }

    @Test
    void dispatchIsANoOpWhenAttemptIsNotInProgress_preventingDuplicateSideEffectsOnReprocessing() {
        DeliveryAttempt a = claimed("user-1", Channel.EMAIL, 1);
        a.setStatus(DeliveryStatus.SUCCESS);
        when(deliveryAttemptRepository.findById(a.getId())).thenReturn(Optional.of(a));

        dispatcher.dispatch(a.getId());

        verify(deliveryAttemptRepository, never()).save(any());
        verify(statusAggregator, never()).refresh(any());
    }

    @Test
    void dispatchIsANoOpWhenAttemptNoLongerExists() {
        UUID missingId = UUID.randomUUID();
        when(deliveryAttemptRepository.findById(missingId)).thenReturn(Optional.empty());

        dispatcher.dispatch(missingId);

        verify(deliveryAttemptRepository, never()).save(any());
    }

    @Test
    void dispatchRoutesRateLimitedFailureToProviderSuppliedRetryAfter() {
        DeliveryAttempt a = claimed("ratelimit-user", Channel.PUSH, 1);
        when(deliveryAttemptRepository.findById(a.getId())).thenReturn(Optional.of(a));

        dispatcher.dispatch(a.getId());

        assertThat(a.getStatus()).isEqualTo(DeliveryStatus.RETRY_SCHEDULED);
        assertThat(a.getNextRetryAt()).isEqualTo(now.plusSeconds(2));
    }
}
