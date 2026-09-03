package com.schwab.nms.domain.enums;

/** Per (recipient, channel) delivery attempt status. */
public enum DeliveryStatus {
    PENDING,
    IN_PROGRESS,
    SUCCESS,
    FAILED,
    RETRY_SCHEDULED,
    ABANDONED,
    SUPPRESSED
}
