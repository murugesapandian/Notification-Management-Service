package com.schwab.nms.api.dto;

import com.schwab.nms.domain.enums.Channel;
import com.schwab.nms.domain.enums.DeliveryStatus;
import com.schwab.nms.domain.enums.FailureCategory;
import com.schwab.nms.domain.enums.RecipientType;
import com.schwab.nms.domain.model.DeliveryAttempt;

import java.time.Instant;

public record DeliveryStatusDto(
        String recipientId,
        RecipientType recipientType,
        Channel channel,
        DeliveryStatus status,
        int attemptCount,
        int maxAttempts,
        FailureCategory lastFailureCategory,
        Instant nextRetryAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static DeliveryStatusDto from(DeliveryAttempt attempt) {
        return new DeliveryStatusDto(
                attempt.getRecipientId(), attempt.getRecipientType(), attempt.getChannel(), attempt.getStatus(),
                attempt.getAttemptCount(), attempt.getMaxAttempts(), attempt.getLastFailureCategory(),
                attempt.getNextRetryAt(), attempt.getCreatedAt(), attempt.getUpdatedAt());
    }
}
