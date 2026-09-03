package com.schwab.nms.application.event;

import java.util.UUID;

/** Published after a notification is durably committed; triggers async routing + queueing. */
public record NotificationSubmittedEvent(UUID notificationId) {
}
