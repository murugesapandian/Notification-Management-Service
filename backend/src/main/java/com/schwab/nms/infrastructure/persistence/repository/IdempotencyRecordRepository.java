package com.schwab.nms.infrastructure.persistence.repository;

import com.schwab.nms.domain.model.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findBySourceSystemAndIdempotencyKey(String sourceSystem, String idempotencyKey);

    List<IdempotencyRecord> findByExpiresAtBefore(Instant cutoff);
}
