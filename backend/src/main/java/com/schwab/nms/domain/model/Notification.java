package com.schwab.nms.domain.model;

import com.schwab.nms.domain.enums.NotificationStatus;
import com.schwab.nms.domain.enums.Priority;
import com.schwab.nms.domain.enums.Severity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for a notification request. The primary key {@code id} doubles as the
 * externally visible "Notification identifier" (section 4.1 of the requirements).
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_source_idem", columnList = "source_system,idempotency_key", unique = false),
        @Index(name = "idx_notification_status", columnList = "overall_status"),
        @Index(name = "idx_notification_event_id", columnList = "event_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"recipients", "requestedChannels"})
public class Notification {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_system", nullable = false, length = 100)
    private String sourceSystem;

    @Column(name = "event_id", length = 200)
    private String eventId;

    @Column(name = "notification_type", nullable = false, length = 100)
    private String notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private Priority priority;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "body", length = 4000)
    private String body;

    @Column(name = "idempotency_key", length = 200)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_status", nullable = false, length = 30)
    private NotificationStatus overallStatus;

    @Column(name = "created_at", nullable = false, updatable = false, length = 30)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, length = 30)
    private Instant updatedAt;

    @Column(name = "scheduled_at", length = 30)
    private Instant scheduledAt;

    @Column(name = "expires_at", length = 30)
    private Instant expiresAt;

    @Column(name = "acknowledged_at", length = 30)
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    @Column(name = "escalated_at", length = 30)
    private Instant escalatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<NotificationRecipient> recipients = new ArrayList<>();

    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RequestedChannel> requestedChannels = new ArrayList<>();

    public void addRecipient(NotificationRecipient recipient) {
        recipient.setNotification(this);
        this.recipients.add(recipient);
    }

    public void addRequestedChannel(RequestedChannel channel) {
        channel.setNotification(this);
        this.requestedChannels.add(channel);
    }
}
