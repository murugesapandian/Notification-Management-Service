package com.schwab.nms.infrastructure.worker;

import com.schwab.nms.config.EscalationProperties;
import com.schwab.nms.domain.enums.Channel;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscalationJobTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private EscalationExecutor executor;

    private EscalationJob job;
    private final Instant now = Instant.parse("2026-01-01T00:30:00Z");
    private final EscalationProperties properties =
            new EscalationProperties(15, "oncall-escalation-group", Channel.SLACK);

    @BeforeEach
    void setUp() {
        job = new EscalationJob(notificationRepository, executor, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private Notification unacknowledgedCritical(UUID id) {
        return Notification.builder()
                .id(id).sourceSystem("src").notificationType("FRAUD_ALERT")
                .severity(Severity.CRITICAL).priority(Priority.URGENT)
                .overallStatus(NotificationStatus.PARTIALLY_DELIVERED)
                .createdAt(now.minusSeconds(3600)).updatedAt(now.minusSeconds(3600)).build();
    }

    @Test
    void delegatesEachEligibleNotificationToTheExecutor() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(notificationRepository.findEligibleForEscalation(any(), any()))
                .thenReturn(List.of(unacknowledgedCritical(id1), unacknowledgedCritical(id2)));

        job.escalateOverdueCriticalNotifications();

        verify(executor).escalateOne(id1);
        verify(executor).escalateOne(id2);
    }

    @Test
    void doesNothingWhenNoNotificationIsOverdue() {
        when(notificationRepository.findEligibleForEscalation(any(), any())).thenReturn(List.of());

        job.escalateOverdueCriticalNotifications();

        verify(executor, never()).escalateOne(any());
    }

    @Test
    void aConcurrentUpdateConflictOnOneNotificationDoesNotStopTheRestOfTheBatch() {
        UUID conflicted = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        when(notificationRepository.findEligibleForEscalation(any(), any()))
                .thenReturn(List.of(unacknowledgedCritical(conflicted), unacknowledgedCritical(healthy)));
        doThrow(new ObjectOptimisticLockingFailureException(Notification.class, conflicted))
                .when(executor).escalateOne(conflicted);

        job.escalateOverdueCriticalNotifications();

        verify(executor).escalateOne(conflicted);
        verify(executor).escalateOne(healthy);
    }
}
