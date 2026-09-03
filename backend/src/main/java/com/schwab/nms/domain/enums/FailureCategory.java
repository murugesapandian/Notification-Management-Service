package com.schwab.nms.domain.enums;

/**
 * Classification of a provider-reported delivery failure. Drives the retry policy
 * (see application.RetryPolicy) — only RETRYABLE categories are eligible for
 * bounded, backed-off retry; the rest fail fast.
 */
public enum FailureCategory {
    TRANSIENT_PROVIDER_FAILURE(true),
    TIMEOUT(true),
    RATE_LIMITED(true),
    PERMANENT_PROVIDER_REJECTION(false),
    INVALID_RECIPIENT(false),
    AUTH_ERROR(false),
    UNKNOWN(false);

    private final boolean retryable;

    FailureCategory(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
