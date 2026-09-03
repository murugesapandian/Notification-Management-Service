package com.schwab.nms.api.dto;

import java.util.List;

public record NotificationListResponse(
        List<NotificationSummaryDto> items,
        int page,
        int size,
        long totalElements
) {
}
