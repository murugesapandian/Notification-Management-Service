package com.schwab.nms.infrastructure.worker;

import com.schwab.nms.domain.model.IdempotencyRecord;
import com.schwab.nms.infrastructure.persistence.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Brownfield addition (docs/scenarios/02-brownfield.md): enforces the retention
 * policy that {@link IdempotencyRecord} only documented until now (section 4.4
 * requires the policy to be "documented" — this makes it actually true rather than
 * aspirational). Records past their 7-day expiry (see
 * NotificationSubmissionService.IDEMPOTENCY_RETENTION) are periodically purged so
 * the table doesn't grow unbounded and so a source system that reuses an
 * idempotency key long after the original event is treated as a new submission,
 * not silently suppressed forever.
 *
 * A single scheduled method calling the repository directly has no self-invocation
 * proxy hazard (contrast with DeliveryDispatcher's javadoc), so no extra bean split
 * is needed here.
 */
@Component
public class IdempotencyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final Clock clock;

    public IdempotencyCleanupJob(IdempotencyRecordRepository idempotencyRecordRepository, Clock clock) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${nms.idempotency.cleanup-interval-ms:3600000}")
    @Transactional
    public void purgeExpired() {
        List<IdempotencyRecord> expired = idempotencyRecordRepository.findByExpiresAtBefore(Instant.now(clock));
        if (expired.isEmpty()) {
            return;
        }
        idempotencyRecordRepository.deleteAll(expired);
        log.info("Purged {} expired idempotency record(s)", expired.size());
    }
}
