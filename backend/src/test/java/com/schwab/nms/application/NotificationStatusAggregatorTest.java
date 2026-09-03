package com.schwab.nms.application;

import com.schwab.nms.domain.enums.Channel;
import com.schwab.nms.domain.enums.DeliveryStatus;
import com.schwab.nms.domain.enums.NotificationStatus;
import com.schwab.nms.domain.enums.Priority;
import com.schwab.nms.domain.enums.RecipientType;
import com.schwab.nms.domain.enums.Severity;
import com.schwab.nms.domain.model.DeliveryAttempt;
import com.schwab.nms.domain.model.Notification;
import com.schwab.nms.infrastructure.persistence.repository.DeliveryAttemptRepository;
import com.schwab.nms.infrastructure.persistence.repository.NotificationRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationStatusAggregatorTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;

    private NotificationStatusAggregator aggregator;
    private UUID notificationId;
    private Notification notification;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        aggregator = new NotificationStatusAggregator(notificationRepository, deliveryAttemptRepository, fixed);
        notificationId = UUID.randomUUID();
        notification = Notification.builder()
                .id(notificationId)
                .sourceSystem("src")
                .notificationType("TYPE")
                .severity(Severity.LOW)
                .priority(Priority.NORMAL)
                .overallStatus(NotificationStatus.QUEUED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
    }

    private DeliveryAttempt attempt(DeliveryStatus status) {
        return DeliveryAttempt.builder()
                .notificationId(notificationId).recipientId("r").recipientType(RecipientType.USER_ID)
                .channel(Channel.EMAIL).status(status).createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    @Test
    void marksInProgressWhenAnyAttemptIsNonTerminal() {
        when(deliveryAttemptRepository.findByNotificationId(notificationId))
                .thenReturn(List.of(attempt(DeliveryStatus.SUCCESS), attempt(DeliveryStatus.PENDING)));

        aggregator.refresh(notificationId);

        assertThat(notification.getOverallStatus()).isEqualTo(NotificationStatus.IN_PROGRESS);
    }

    @Test
    void marksDeliveredWhenAllAttemptsSucceed() {
        when(deliveryAttemptRepository.findByNotificationId(notificationId))
                .thenReturn(List.of(attempt(DeliveryStatus.SUCCESS), attempt(DeliveryStatus.SUCCESS)));

        aggregator.refresh(notificationId);

        assertThat(notification.getOverallStatus()).isEqualTo(NotificationStatus.DELIVERED);
    }

    @Test
    void marksPartiallyDeliveredWhenSomeSucceedAndSomeFail() {
        when(deliveryAttemptRepository.findByNotificationId(notificationId))
                .thenReturn(List.of(attempt(DeliveryStatus.SUCCESS), attempt(DeliveryStatus.ABANDONED)));

        aggregator.refresh(notificationId);

        assertThat(notification.getOverallStatus()).isEqualTo(NotificationStatus.PARTIALLY_DELIVERED);
    }

    @Test
    void marksFailedWhenNoAttemptSucceeds() {
        when(deliveryAttemptRepository.findByNotificationId(notificationId))
                .thenReturn(List.of(attempt(DeliveryStatus.ABANDONED), attempt(DeliveryStatus.SUPPRESSED)));

        aggregator.refresh(notificationId);

        assertThat(notification.getOverallStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void doesNothingWhenThereAreNoAttemptsYet() {
        when(deliveryAttemptRepository.findByNotificationId(notificationId)).thenReturn(List.of());

        aggregator.refresh(notificationId);

        assertThat(notification.getOverallStatus()).isEqualTo(NotificationStatus.QUEUED);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void doesNotOverwriteExpiredTerminalStatus() {
        notification.setOverallStatus(NotificationStatus.EXPIRED);

        aggregator.refresh(notificationId);

        verify(deliveryAttemptRepository, never()).findByNotificationId(any());
        assertThat(notification.getOverallStatus()).isEqualTo(NotificationStatus.EXPIRED);
    }

    @Test
    void doesNotOverwriteEscalatedStatusWhenTheEscalationAttemptItselfIsDispatched() {
        notification.setOverallStatus(NotificationStatus.ESCALATED);

        aggregator.refresh(notificationId);

        verify(deliveryAttemptRepository, never()).findByNotificationId(any());
        assertThat(notification.getOverallStatus()).isEqualTo(NotificationStatus.ESCALATED);
    }
}
