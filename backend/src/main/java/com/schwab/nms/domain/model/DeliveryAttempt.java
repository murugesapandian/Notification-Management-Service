package com.schwab.nms.domain.model;

import com.schwab.nms.domain.enums.Channel;
import com.schwab.nms.domain.enums.DeliveryStatus;
import com.schwab.nms.domain.enums.FailureCategory;
import com.schwab.nms.domain.enums.RecipientType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single (recipient, channel) delivery unit. This is also the delivery-level
 * idempotency/dedup boundary: the unique constraint on
 * (notification_id, recipient_id, channel) in V1, hardened by V2's row-locking
 * poller (see docs/scenarios/02-brownfield.md), ensures a reprocessed/duplicated
 * queue pickup cannot fan out into a second real send for the same unit of work.
 */
@Entity
@Table(name = "delivery_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_delivery_unit",
                columnNames = {"notification_id", "recipient_id", "channel"}),
        indexes = {
                @Index(name = "idx_delivery_status", columnList = "status"),
                @Index(name = "idx_delivery_next_retry", columnList = "status,next_retry_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryAttempt {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Column(name = "recipient_id", nullable = false, length = 200)
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 20)
    private RecipientType recipientType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 5;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_failure_category", length = 40)
    private FailureCategory lastFailureCategory;

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public boolean isTerminal() {
        return status == DeliveryStatus.SUCCESS
                || status == DeliveryStatus.ABANDONED
                || status == DeliveryStatus.SUPPRESSED;
    }
}
