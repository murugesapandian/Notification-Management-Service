package com.schwab.nms.api.dto;

import com.schwab.nms.domain.enums.NotificationStatus;
import com.schwab.nms.domain.model.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID notificationId,
        NotificationStatus status,
        boolean duplicate,
        Instant createdAt
) {
    public static NotificationResponse from(Notification notification, boolean duplicate) {
        return new NotificationResponse(notification.getId(), notification.getOverallStatus(), duplicate,
                notification.getCreatedAt());
    }
}
