package com.schwab.nms.application;

import com.schwab.nms.domain.enums.FailureCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Bounded exponential-backoff retry policy (section 4.5).
 *
 * Only {@link FailureCategory#isRetryable()} categories (TRANSIENT_PROVIDER_FAILURE,
 * TIMEOUT, RATE_LIMITED) are retried, and only up to {@code maxAttempts}. Everything
 * else (PERMANENT_PROVIDER_REJECTION, INVALID_RECIPIENT, AUTH_ERROR) fails fast and
 * moves straight to ABANDONED so it can surface for manual remediation instead of
 * being retried forever.
 *
 * Base/max delay are externalized (not hardcoded) specifically so integration tests
 * can run the real retry path on a short clock (application-test.yml uses 1s/5s)
 * instead of waiting out a production-realistic 30s/15min backoff.
 */
@Component
public class RetryPolicy {

    private final Duration baseDelay;
    private final Duration maxDelay;

    public RetryPolicy(
            @Value("${nms.retry.base-delay-seconds:30}") long baseDelaySeconds,
            @Value("${nms.retry.max-delay-seconds:900}") long maxDelaySeconds) {
        this.baseDelay = Duration.ofSeconds(baseDelaySeconds);
        this.maxDelay = Duration.ofSeconds(maxDelaySeconds);
    }

    public boolean shouldRetry(FailureCategory category, int attemptCount, int maxAttempts) {
        return category != null && category.isRetryable() && attemptCount < maxAttempts;
    }

    /** Computes the next retry time. Honors a provider-supplied Retry-After when present. */
    public Instant nextRetryAt(Instant now, int attemptCount, Integer providerRetryAfterSeconds) {
        if (providerRetryAfterSeconds != null) {
            return now.plusSeconds(providerRetryAfterSeconds);
        }
        long backoffSeconds = baseDelay.getSeconds() * (1L << Math.max(0, attemptCount - 1));
        Duration delay = Duration.ofSeconds(Math.min(backoffSeconds, maxDelay.getSeconds()));
        return now.plus(delay);
    }
}
