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
 *
 * Deletion is a single bulk statement ({@code deleteByExpiresAtBefore}), not the
 * earlier find-all-then-deleteAll — that pattern loaded every expired row into a
 * Java List and the persistence context before removing them one at a time, which
 * would spike heap usage in direct proportion to backlog size if this job were ever
 * disabled for a while (or the interval set very long) and let a large backlog
 * accumulate. Found during a resource-usage review, not exercised by tests since it
 * required a large backlog to reproduce — fixed at the source instead.
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
        int purged = idempotencyRecordRepository.deleteByExpiresAtBefore(Instant.now(clock));
        if (purged > 0) {
            log.info("Purged {} expired idempotency record(s)", purged);
        }
    }
}
