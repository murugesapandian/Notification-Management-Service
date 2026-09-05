package com.schwab.nms.infrastructure.persistence.repository;

import com.schwab.nms.domain.model.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findBySourceSystemAndIdempotencyKey(String sourceSystem, String idempotencyKey);

    /**
     * Single bulk DELETE, not a load-then-delete-one-by-one — deliberately so
     * {@link com.schwab.nms.infrastructure.worker.IdempotencyCleanupJob} never has
     * to materialize an unbounded number of expired entities in the JVM heap just
     * to remove them (found during a resource-usage review; see that class's
     * javadoc). Returns the row count for logging.
     */
    @Modifying
    int deleteByExpiresAtBefore(Instant cutoff);
}
