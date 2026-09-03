package com.schwab.nms.domain.enums;

/**
 * Delivery channels supported by the platform (greenfield: section 4.1/4.3).
 * SLACK was added by the brownfield scenario (docs/scenarios/02-brownfield.md) —
 * adding it required a new enum value, a new ChannelProvider implementation, and a
 * schema seed migration, but zero changes to RoutingService or DeliveryDispatcher's
 * dispatch logic, which is exactly the point of the registry refactor described there.
 */
public enum Channel {
    EMAIL,
    SMS,
    PUSH,
    SLACK
}
