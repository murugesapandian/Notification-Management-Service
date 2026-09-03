package com.schwab.nms.application.command;

import com.schwab.nms.domain.enums.Channel;
import com.schwab.nms.domain.enums.Priority;
import com.schwab.nms.domain.enums.RecipientType;
import com.schwab.nms.domain.enums.Severity;

import java.time.Instant;
import java.util.List;

/** Transport-agnostic input to {@code NotificationSubmissionService}. */
public record SubmitNotificationCommand(
        String sourceSystem,
        String eventId,
        String notificationType,
        Severity severity,
        Priority priority,
        String subject,
        String body,
        List<RecipientInput> recipients,
        List<Channel> requestedChannels,
        String idempotencyKey,
        Instant scheduledAt,
        Instant expiresAt
) {
    public record RecipientInput(String recipientId, RecipientType recipientType) {
    }
}
