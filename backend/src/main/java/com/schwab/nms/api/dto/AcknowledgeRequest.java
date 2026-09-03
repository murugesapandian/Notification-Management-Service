package com.schwab.nms.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcknowledgeRequest(
        @NotBlank(message = "acknowledgedBy is required")
        @Size(max = 200)
        String acknowledgedBy
) {
}
