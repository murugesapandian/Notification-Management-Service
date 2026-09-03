package com.schwab.nms.infrastructure.provider;

import com.schwab.nms.domain.enums.RecipientType;

import java.util.UUID;

/** Everything a {@link ChannelProvider} needs to attempt a single send. */
public record DeliveryContext(
        UUID notificationId,
        UUID deliveryAttemptId,
        String recipientId,
        RecipientType recipientType,
        String subject,
        String body,
        int attemptNumber
) {
}
