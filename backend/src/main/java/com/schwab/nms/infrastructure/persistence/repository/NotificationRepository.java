package com.schwab.nms.infrastructure.persistence.repository;

import com.schwab.nms.domain.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Eligibility for the ambiguous-requirement escalation feature (section
     * docs/scenarios/03-ambiguous-requirements.md): CRITICAL, never acknowledged,
     * never already escalated, created before the cutoff, and not in a terminal
     * side-state (EXPIRED/REJECTED/DUPLICATE_SUPPRESSED) where escalation would be
     * meaningless — a rejected or deduplicated notification was never actually
     * sent, so there is nothing to escalate.
     *
     * Takes a {@link Pageable} (found during a resource-usage review — this was
     * previously unbounded) so a genuine incident storm producing many CRITICAL
     * alerts at once can't make one poll load an unbounded result set into memory;
     * {@link com.schwab.nms.infrastructure.worker.EscalationJob} caps it to a fixed
     * batch and simply picks the rest up on its next poll, the same batching
     * pattern already used by DeliveryAttemptRepository.lockNextBatchDue.
     */
    @Query("select n from Notification n where n.severity = com.schwab.nms.domain.enums.Severity.CRITICAL "
            + "and n.acknowledgedAt is null and n.escalatedAt is null and n.createdAt <= :cutoff "
            + "and n.overallStatus not in ("
            + "com.schwab.nms.domain.enums.NotificationStatus.EXPIRED, "
            + "com.schwab.nms.domain.enums.NotificationStatus.REJECTED, "
            + "com.schwab.nms.domain.enums.NotificationStatus.DUPLICATE_SUPPRESSED) "
            + "order by n.createdAt asc")
    List<Notification> findEligibleForEscalation(@Param("cutoff") Instant cutoff, Pageable pageable);
}
