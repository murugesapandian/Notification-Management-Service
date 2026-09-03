package com.schwab.nms.domain.enums;

/** Delivery priority requested by the source system; influences worker dispatch ordering. */
public enum Priority {
    URGENT,
    HIGH,
    NORMAL,
    LOW
}
