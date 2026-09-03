package com.schwab.nms.domain.enums;

/**
 * Delivery channels supported by the platform (greenfield: section 4.1/4.3).
 * The brownfield scenario (docs/scenarios/02-brownfield.md) adds SLACK here.
 */
public enum Channel {
    EMAIL,
    SMS,
    PUSH
}
