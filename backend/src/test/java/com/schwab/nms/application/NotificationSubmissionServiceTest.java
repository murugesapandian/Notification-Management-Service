package com.schwab.nms.application;

import com.schwab.nms.application.command.SubmitNotificationCommand;
import com.schwab.nms.application.event.NotificationSubmittedEvent;
import com.schwab.nms.domain.enums.Priority;
import com.schwab.nms.domain.enums.RecipientType;
import com.schwab.nms.domain.enums.Severity;
import com.schwab.nms.domain.model.IdempotencyRecord;
import com.schwab.nms.domain.model.Notification;
import com.schwab.nms.infrastructure.persistence.repository.IdempotencyRecordRepository;
import com.schwab.nms.infrastructure.persistence.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSubmissionServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NotificationSubmissionService service;
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(now, ZoneOffset.UTC);
        service = new NotificationSubmissionService(notificationRepository, idempotencyRecordRepository,
                auditService, eventPublisher, fixed);
        lenient().when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private SubmitNotificationCommand command(String idempotencyKey) {
        return new SubmitNotificationCommand(
                "trading-platform", "evt-1", "TRADE_ALERT", Severity.HIGH, Priority.HIGH,
                "subject", "body",
                List.of(new SubmitNotificationCommand.RecipientInput("user-1", RecipientType.USER_ID)),
                List.of(), idempotencyKey, null, null);
    }

    @Test
    void createsNewNotificationAndPublishesEventWhenNoIdempotencyKeyGiven() {
        NotificationSubmissionResult result = service.submit(command(null));

        assertThat(result.duplicate()).isFalse();
        assertThat(result.notification().getSourceSystem()).isEqualTo("trading-platform");
        verify(eventPublisher).publishEvent(any(NotificationSubmittedEvent.class));
        verifyNoInteractions(idempotencyRecordRepository);
    }

    @Test
    void createsIdempotencyRecordWithSevenDayRetentionWhenKeyGiven() {
        when(idempotencyRecordRepository.findBySourceSystemAndIdempotencyKey("trading-platform", "key-1"))
                .thenReturn(Optional.empty());

        service.submit(command("key-1"));

        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(now.plus(java.time.Duration.ofDays(7)));
    }

    @Test
    void returnsExistingNotificationForRepeatSubmissionWithinRetentionWindow() {
        UUID existingId = UUID.randomUUID();
        Notification existing = Notification.builder().id(existingId).sourceSystem("trading-platform")
                .notificationType("TRADE_ALERT").severity(Severity.HIGH).priority(Priority.HIGH)
                .overallStatus(com.schwab.nms.domain.enums.NotificationStatus.QUEUED)
                .createdAt(now).updatedAt(now).build();
        IdempotencyRecord record = IdempotencyRecord.builder()
                .sourceSystem("trading-platform").idempotencyKey("key-1").notificationId(existingId)
                .createdAt(now.minusSeconds(60)).expiresAt(now.plusSeconds(60)).build();

        when(idempotencyRecordRepository.findBySourceSystemAndIdempotencyKey("trading-platform", "key-1"))
                .thenReturn(Optional.of(record));
        when(notificationRepository.findById(existingId)).thenReturn(Optional.of(existing));

        NotificationSubmissionResult result = service.submit(command("key-1"));

        assertThat(result.duplicate()).isTrue();
        assertThat(result.notification().getId()).isEqualTo(existingId);
        verify(notificationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(auditService).record(eq(existingId), eq(com.schwab.nms.domain.enums.AuditAction.DUPLICATE_SUPPRESSED), any());
    }

    @Test
    void treatsExpiredIdempotencyRecordAsANewSubmission() {
        UUID existingId = UUID.randomUUID();
        IdempotencyRecord expiredRecord = IdempotencyRecord.builder()
                .sourceSystem("trading-platform").idempotencyKey("key-1").notificationId(existingId)
                .createdAt(now.minusSeconds(700000)).expiresAt(now.minusSeconds(1)).build();

        when(idempotencyRecordRepository.findBySourceSystemAndIdempotencyKey("trading-platform", "key-1"))
                .thenReturn(Optional.of(expiredRecord));

        NotificationSubmissionResult result = service.submit(command("key-1"));

        assertThat(result.duplicate()).isFalse();
        assertThat(result.notification().getId()).isNotEqualTo(existingId);
        verify(eventPublisher).publishEvent(any(NotificationSubmittedEvent.class));
    }

    @Test
    void resolvesConcurrentRaceOnSameIdempotencyKeyToTheWinningRow() {
        UUID winnerId = UUID.randomUUID();
        when(idempotencyRecordRepository.findBySourceSystemAndIdempotencyKey("trading-platform", "key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(IdempotencyRecord.builder()
                        .sourceSystem("trading-platform").idempotencyKey("key-1").notificationId(winnerId)
                        .createdAt(now).expiresAt(now.plusSeconds(600)).build()));
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        Notification winner = Notification.builder().id(winnerId).sourceSystem("trading-platform")
                .notificationType("TRADE_ALERT").severity(Severity.HIGH).priority(Priority.HIGH)
                .overallStatus(com.schwab.nms.domain.enums.NotificationStatus.QUEUED)
                .createdAt(now).updatedAt(now).build();
        when(notificationRepository.findById(winnerId)).thenReturn(Optional.of(winner));

        NotificationSubmissionResult result = service.submit(command("key-1"));

        assertThat(result.duplicate()).isTrue();
        assertThat(result.notification().getId()).isEqualTo(winnerId);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
