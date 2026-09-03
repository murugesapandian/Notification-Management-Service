package com.schwab.nms.api.dto;

import com.schwab.nms.domain.enums.NotificationStatus;
import com.schwab.nms.domain.enums.Priority;
import com.schwab.nms.domain.enums.Severity;
import com.schwab.nms.domain.model.Notification;

import java.time.Instant;
import java.util.UUID;

/** Lightweight row for the notification list/dashboard view — not part of section 4's spec,
 * added so the UI has something to list; see docs/architecture-overview.md. */
public record NotificationSummaryDto(
        UUID notificationId,
        String sourceSystem,
        String notificationType,
        Severity severity,
        Priority priority,
        NotificationStatus overallStatus,
        Instant createdAt,
        Instant escalatedAt
) {
    public static NotificationSummaryDto from(Notification n) {
        return new NotificationSummaryDto(n.getId(), n.getSourceSystem(), n.getNotificationType(),
                n.getSeverity(), n.getPriority(), n.getOverallStatus(), n.getCreatedAt(), n.getEscalatedAt());
    }
}
