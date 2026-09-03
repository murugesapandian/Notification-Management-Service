package com.schwab.nms.domain.enums;

/**
 * Overall lifecycle status of a notification. See docs/architecture-overview.md
 * for the full state diagram and transition table.
 *
 * RECEIVED -> VALIDATED -> ROUTED -> QUEUED -> IN_PROGRESS -> {DELIVERED, PARTIALLY_DELIVERED, FAILED}
 * Terminal side-states: REJECTED, EXPIRED, DUPLICATE_SUPPRESSED
 * Escalation extension (ambiguous scenario, see docs/scenarios/03-ambiguous-requirements.md): ESCALATED
 */
public enum NotificationStatus {
    RECEIVED,
    VALIDATED,
    ROUTED,
    QUEUED,
    IN_PROGRESS,
    DELIVERED,
    PARTIALLY_DELIVERED,
    FAILED,
    REJECTED,
    EXPIRED,
    DUPLICATE_SUPPRESSED,
    ESCALATED
}
