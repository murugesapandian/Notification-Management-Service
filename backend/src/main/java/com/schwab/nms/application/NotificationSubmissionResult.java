package com.schwab.nms.application;

import com.schwab.nms.domain.model.Notification;

public record NotificationSubmissionResult(Notification notification, boolean duplicate) {
}
