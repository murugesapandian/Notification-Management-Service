package com.schwab.nms.infrastructure.worker;

import com.schwab.nms.application.AuditService;
import com.schwab.nms.config.EscalationProperties;
import com.schwab.nms.domain.enums.Channel;
import com.schwab.nms.domain.enums.NotificationStatus;
import com.schwab.nms.domain.enums.Priority;
import com.schwab.nms.domain.enums.Severity;
import com.schwab.nms.domain.model.DeliveryAttempt;
import com.schwab.nms.domain.model.Notification;
import com.schwab.nms.infrastructure.persistence.repository.DeliveryAttemptRepository;
import com.schwab.nms.infrastructure.persistence.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscalationExecutorTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;
    @Mock
    private AuditService auditService;

    private EscalationExecutor executor;
    private final Instant now = Instant.parse("2026-01-01T00:30:00Z");
    private final EscalationProperties properties =
            new EscalationProperties(15, "oncall-escalation-group", Channel.SLACK);

    @BeforeEach
    void setUp() {
        executor = new EscalationExecutor(notificationRepository, deliveryAttemptRepository, auditService, properties,
                Clock.fixed(now, ZoneOffset.UTC));
        lenient().when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Notification unacknowledgedCritical(UUID id) {
        return Notification.builder()
                .id(id).sourceSystem("src").notificationType("FRAUD_ALERT")
                .severity(Severity.CRITICAL).priority(Priority.URGENT)
                .overallStatus(NotificationStatus.PARTIALLY_DELIVERED)
                .createdAt(now.minusSeconds(3600)).updatedAt(now.minusSeconds(3600)).build();
    }

    @Test
    void escalatesAnEligibleNotification() {
        UUID id = UUID.randomUUID();
        Notification notification = unacknowledgedCritical(id);
        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));

        executor.escalateOne(id);

        assertThat(notification.getOverallStatus()).isEqualTo(NotificationStatus.ESCALATED);
        assertThat(notification.getEscalatedAt()).isEqualTo(now);

        ArgumentCaptor<DeliveryAttempt> captor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(deliveryAttemptRepository).save(captor.capture());
        assertThat(captor.getValue().getNotificationId()).isEqualTo(id);
        assertThat(captor.getValue().getRecipientId()).isEqualTo("oncall-escalation-group");
        assertThat(captor.getValue().getChannel()).isEqualTo(Channel.SLACK);
    }

    @Test
    void reCheckIsANoOpIfTheFreshlyLoadedRowWasAcknowledgedSinceTheBatchQueryRan() {
        UUID id = UUID.randomUUID();
        Notification notification = unacknowledgedCritical(id);
        notification.setAcknowledgedAt(now.minusSeconds(5));
        notification.setAcknowledgedBy("oncall.jane");
        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));

        executor.escalateOne(id);

        assertThat(notification.getOverallStatus()).isNotEqualTo(NotificationStatus.ESCALATED);
        verify(deliveryAttemptRepository, never()).save(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void stillMarksNotificationEscalatedWhenTheDeliveryUnitAlreadyExisted() {
        UUID id = UUID.randomUUID();
        Notification notification = unacknowledgedCritical(id);
        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));
        when(deliveryAttemptRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        executor.escalateOne(id);

        assertThat(notification.getOverallStatus()).isEqualTo(NotificationStatus.ESCALATED);
    }
}
