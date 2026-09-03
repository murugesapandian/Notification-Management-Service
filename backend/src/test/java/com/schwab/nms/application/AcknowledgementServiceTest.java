package com.schwab.nms.application;

import com.schwab.nms.domain.enums.AuditAction;
import com.schwab.nms.domain.enums.NotificationStatus;
import com.schwab.nms.domain.enums.Priority;
import com.schwab.nms.domain.enums.Severity;
import com.schwab.nms.domain.model.Notification;
import com.schwab.nms.infrastructure.persistence.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcknowledgementServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private AuditService auditService;

    private AcknowledgementService service;
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private UUID notificationId;
    private Notification notification;

    @BeforeEach
    void setUp() {
        service = new AcknowledgementService(notificationRepository, auditService, Clock.fixed(now, ZoneOffset.UTC));
        notificationId = UUID.randomUUID();
        notification = Notification.builder()
                .id(notificationId).sourceSystem("src").notificationType("TYPE")
                .severity(Severity.CRITICAL).priority(Priority.URGENT)
                .overallStatus(NotificationStatus.QUEUED)
                .createdAt(now.minusSeconds(60)).updatedAt(now.minusSeconds(60)).build();
        lenient().when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
    }

    @Test
    void acknowledgesAndRecordsAudit() {
        service.acknowledge(notificationId, "oncall.jane");

        assertThat(notification.getAcknowledgedAt()).isEqualTo(now);
        assertThat(notification.getAcknowledgedBy()).isEqualTo("oncall.jane");
        verify(auditService).record(eq(notificationId), eq(AuditAction.NOTIFICATION_ACKNOWLEDGED), any(), eq("oncall.jane"));
    }

    @Test
    void firstAcknowledgementWinsOnRepeatCalls() {
        service.acknowledge(notificationId, "oncall.jane");
        Instant firstAckTime = notification.getAcknowledgedAt();

        service.acknowledge(notificationId, "oncall.bob");

        assertThat(notification.getAcknowledgedBy()).isEqualTo("oncall.jane");
        assertThat(notification.getAcknowledgedAt()).isEqualTo(firstAckTime);
        verify(auditService, org.mockito.Mockito.times(1))
                .record(eq(notificationId), eq(AuditAction.NOTIFICATION_ACKNOWLEDGED), any(), any());
    }

    @Test
    void throwsWhenNotificationDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(notificationRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acknowledge(missingId, "someone"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
