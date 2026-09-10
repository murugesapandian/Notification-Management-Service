package com.schwab.nms.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Request-level dedup boundary (section 4.4). The natural key is
 * (source_system, idempotency_key); a repeat submission within the retention
 * window resolves to the original notification instead of creating a new one.
 * Retention policy: 7 days (see docs/scenarios/02-brownfield.md and
 * IdempotencyCleanupJob), matching the typical event-correlation / retry window
 * of upstream source systems.
 */
@Entity
@Table(name = "idempotency_records",
        uniqueConstraints = @UniqueConstraint(name = "uq_idem_source_key", columnNames = {"source_system", "idempotency_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "source_system", nullable = false, length = 100)
    private String sourceSystem;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "notification_id", nullable = false, length = 36)
    private UUID notificationId;

    @Column(name = "created_at", nullable = false, length = 30)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, length = 30)
    private Instant expiresAt;
}
