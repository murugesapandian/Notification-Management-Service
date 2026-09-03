package com.schwab.nms.infrastructure.worker;

import com.schwab.nms.domain.model.IdempotencyRecord;
import com.schwab.nms.infrastructure.persistence.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyCleanupJobTest {

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    private IdempotencyCleanupJob job;
    private final Instant now = Instant.parse("2026-01-08T00:00:00Z");

    @BeforeEach
    void setUp() {
        job = new IdempotencyCleanupJob(idempotencyRecordRepository, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void purgesRecordsPastTheirRetentionWindow() {
        List<IdempotencyRecord> expired = List.of(
                IdempotencyRecord.builder().id(UUID.randomUUID()).sourceSystem("src").idempotencyKey("k1")
                        .notificationId(UUID.randomUUID()).createdAt(now.minusSeconds(700000))
                        .expiresAt(now.minusSeconds(1)).build());
        when(idempotencyRecordRepository.findByExpiresAtBefore(eq(now))).thenReturn(expired);

        job.purgeExpired();

        verify(idempotencyRecordRepository).deleteAll(expired);
    }

    @Test
    void doesNothingWhenNothingHasExpiredYet() {
        when(idempotencyRecordRepository.findByExpiresAtBefore(eq(now))).thenReturn(List.of());

        job.purgeExpired();

        verify(idempotencyRecordRepository, never()).deleteAll(any());
    }
}
