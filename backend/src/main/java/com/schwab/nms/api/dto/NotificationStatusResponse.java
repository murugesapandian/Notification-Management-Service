package com.schwab.nms.api.dto;

import com.schwab.nms.domain.enums.Channel;
import com.schwab.nms.domain.enums.NotificationStatus;
import com.schwab.nms.domain.model.DeliveryAttempt;
import com.schwab.nms.domain.model.Notification;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Response payload for GET /api/v1/notifications/{id} (section 4.2). */
public record NotificationStatusResponse(
        UUID notificationId,
        String sourceSystem,
        String eventId,
        NotificationStatus overallStatus,
        Set<Channel> selectedChannels,
        List<DeliveryStatusDto> deliveries,
        Instant createdAt,
        Instant updatedAt,
        Instant scheduledAt,
        Instant expiresAt,
        Instant acknowledgedAt,
        Instant escalatedAt
) {
    public static NotificationStatusResponse from(Notification notification, List<DeliveryAttempt> attempts) {
        Set<Channel> selected = attempts.stream().map(DeliveryAttempt::getChannel).collect(Collectors.toSet());
        List<DeliveryStatusDto> deliveries = attempts.stream().map(DeliveryStatusDto::from).toList();
        return new NotificationStatusResponse(
                notification.getId(), notification.getSourceSystem(), notification.getEventId(),
                notification.getOverallStatus(), selected, deliveries,
                notification.getCreatedAt(), notification.getUpdatedAt(),
                notification.getScheduledAt(), notification.getExpiresAt(),
                notification.getAcknowledgedAt(), notification.getEscalatedAt());
    }
}
