package com.schwab.nms.api.dto;

import com.schwab.nms.domain.enums.Channel;
import com.schwab.nms.domain.enums.Priority;
import com.schwab.nms.domain.enums.RecipientType;
import com.schwab.nms.domain.enums.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Request payload for POST /api/v1/notifications (section 4.1). */
public record NotificationRequest(

        @NotBlank(message = "sourceSystem is required")
        @Size(max = 100)
        String sourceSystem,

        @Size(max = 200)
        String eventId,

        @NotBlank(message = "notificationType is required")
        @Size(max = 100)
        String notificationType,

        @NotNull(message = "severity is required")
        Severity severity,

        @NotNull(message = "priority is required")
        Priority priority,

        @Size(max = 500)
        String subject,

        @Size(max = 4000)
        String body,

        @NotEmpty(message = "at least one recipient is required")
        @Valid
        List<RecipientDto> recipients,

        List<Channel> requestedChannels,

        @Size(max = 200)
        String idempotencyKey,

        Instant scheduledAt,

        Instant expiresAt
) {
    public record RecipientDto(
            @NotBlank(message = "recipientId is required") String recipientId,
            @NotNull(message = "recipientType is required") RecipientType recipientType
    ) {
    }
}
