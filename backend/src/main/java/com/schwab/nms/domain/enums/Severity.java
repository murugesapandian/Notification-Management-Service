package com.schwab.nms.domain.enums;

/** Business severity of the underlying event, independent of delivery priority. */
public enum Severity {
    CRITICAL(4),
    HIGH(3),
    MEDIUM(2),
    LOW(1),
    INFO(0);

    private final int rank;

    Severity(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean atLeast(Severity other) {
        return this.rank >= other.rank;
    }
}
