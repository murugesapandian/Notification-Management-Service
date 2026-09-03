package com.schwab.nms.domain.model;

import com.schwab.nms.domain.enums.Channel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Audit record of how a channel was resolved for a given recipient (section 4.3 / 4.9). */
@Entity
@Table(name = "routing_decisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutingDecision {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Column(name = "recipient_id", nullable = false, length = 200)
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_channel", length = 20)
    private Channel requestedChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolved_channel", nullable = false, length = 20)
    private Channel resolvedChannel;

    @Column(name = "reason", nullable = false, length = 300)
    private String reason;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;
}
