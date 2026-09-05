package com.schwab.nms.infrastructure.worker;

import com.schwab.nms.infrastructure.persistence.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.eq;
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
    void purgesRecordsPastTheirRetentionWindowViaASingleBulkDelete() {
        when(idempotencyRecordRepository.deleteByExpiresAtBefore(eq(now))).thenReturn(3);

        job.purgeExpired();

        verify(idempotencyRecordRepository).deleteByExpiresAtBefore(now);
    }

    @Test
    void doesNothingNotableWhenNothingHasExpiredYet() {
        when(idempotencyRecordRepository.deleteByExpiresAtBefore(eq(now))).thenReturn(0);

        job.purgeExpired();

        verify(idempotencyRecordRepository).deleteByExpiresAtBefore(now);
    }
}
