package com.schwab.nms.domain.model;

import com.schwab.nms.domain.enums.AuditAction;
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
 * Append-only audit trail (section 4.9). {@code detail} intentionally stores only
 * structured, non-sensitive metadata (status codes, channel names, attempt counts) —
 * never raw message bodies, PII payloads, or provider credentials.
 */
@Entity
@Table(name = "audit_events", indexes = @Index(name = "idx_audit_notification", columnList = "notification_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "notification_id", nullable = false, length = 36)
    private UUID notificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private AuditAction action;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "actor", length = 100)
    private String actor;

    @Column(name = "occurred_at", nullable = false, length = 30)
    private Instant occurredAt;
}
