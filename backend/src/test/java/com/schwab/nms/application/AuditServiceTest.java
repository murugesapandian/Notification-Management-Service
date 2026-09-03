package com.schwab.nms.application;

import com.schwab.nms.domain.enums.AuditAction;
import com.schwab.nms.domain.model.AuditEvent;
import com.schwab.nms.infrastructure.persistence.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        auditService = new AuditService(auditEventRepository, fixed);
    }

    @Test
    void recordsEventWithDefaultActorAndTimestamp() {
        UUID notificationId = UUID.randomUUID();

        auditService.record(notificationId, AuditAction.NOTIFICATION_ACCEPTED, "detail");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        AuditEvent saved = captor.getValue();
        assertThat(saved.getNotificationId()).isEqualTo(notificationId);
        assertThat(saved.getAction()).isEqualTo(AuditAction.NOTIFICATION_ACCEPTED);
        assertThat(saved.getDetail()).isEqualTo("detail");
        assertThat(saved.getActor()).isEqualTo("system");
        assertThat(saved.getOccurredAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void truncatesOverlyLongDetailToProtectStorageAndAvoidLeakingLargePayloads() {
        UUID notificationId = UUID.randomUUID();
        String longDetail = "x".repeat(2000);

        auditService.record(notificationId, AuditAction.DELIVERY_FAILED, longDetail);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        assertThat(captor.getValue().getDetail()).hasSize(1000);
    }

    @Test
    void allowsNullDetail() {
        UUID notificationId = UUID.randomUUID();

        auditService.record(notificationId, AuditAction.NOTIFICATION_ACKNOWLEDGED, null, "user@schwab.com");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        assertThat(captor.getValue().getDetail()).isNull();
        assertThat(captor.getValue().getActor()).isEqualTo("user@schwab.com");
    }
}
