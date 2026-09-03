package com.schwab.nms.infrastructure.persistence.repository;

import com.schwab.nms.domain.enums.DeliveryStatus;
import com.schwab.nms.domain.model.DeliveryAttempt;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {

    List<DeliveryAttempt> findByNotificationId(UUID notificationId);

    /**
     * Brownfield hardening (docs/scenarios/02-brownfield.md): PESSIMISTIC_WRITE with a
     * zero lock-timeout hint prevents two concurrent worker threads/instances from
     * picking up and re-sending the same due attempt — a locked row raises a lock
     * failure immediately instead of blocking the poller. The greenfield poller used a
     * plain, unlocked SELECT which was safe for a single worker instance but not for
     * horizontal scale-out. This JPQL form is deliberately portable across H2 (dev/test)
     * and PostgreSQL (prod); a production deployment on Postgres could instead use a
     * native "FOR UPDATE SKIP LOCKED" query for true skip-locked semantics — see the
     * trade-off note in docs/scenarios/02-brownfield.md.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("select d from DeliveryAttempt d "
            + "where d.status in (com.schwab.nms.domain.enums.DeliveryStatus.PENDING, com.schwab.nms.domain.enums.DeliveryStatus.RETRY_SCHEDULED) "
            + "and (d.nextRetryAt is null or d.nextRetryAt <= :now) "
            + "order by d.createdAt asc")
    List<DeliveryAttempt> lockNextBatchDue(@Param("now") Instant now, Pageable pageable);

    long countByNotificationIdAndStatus(UUID notificationId, DeliveryStatus status);
}
