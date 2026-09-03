package com.schwab.nms.application;

import com.schwab.nms.domain.enums.FailureCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private final RetryPolicy retryPolicy = new RetryPolicy(30, 900);

    @ParameterizedTest
    @EnumSource(value = FailureCategory.class, names = {"TRANSIENT_PROVIDER_FAILURE", "TIMEOUT", "RATE_LIMITED"})
    void retriesRetryableCategoriesUnderMaxAttempts(FailureCategory category) {
        assertThat(retryPolicy.shouldRetry(category, 1, 5)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = FailureCategory.class,
            names = {"PERMANENT_PROVIDER_REJECTION", "INVALID_RECIPIENT", "AUTH_ERROR", "UNKNOWN"})
    void doesNotRetryNonRetryableCategories(FailureCategory category) {
        assertThat(retryPolicy.shouldRetry(category, 1, 5)).isFalse();
    }

    @Test
    void stopsRetryingOnceMaxAttemptsReached() {
        assertThat(retryPolicy.shouldRetry(FailureCategory.TIMEOUT, 5, 5)).isFalse();
        assertThat(retryPolicy.shouldRetry(FailureCategory.TIMEOUT, 4, 5)).isTrue();
    }

    @Test
    void nullCategoryIsNeverRetried() {
        assertThat(retryPolicy.shouldRetry(null, 1, 5)).isFalse();
    }

    @Test
    void backoffGrowsExponentiallyAndCapsAt15Minutes() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(retryPolicy.nextRetryAt(now, 1, null)).isEqualTo(now.plusSeconds(30));
        assertThat(retryPolicy.nextRetryAt(now, 2, null)).isEqualTo(now.plusSeconds(60));
        assertThat(retryPolicy.nextRetryAt(now, 3, null)).isEqualTo(now.plusSeconds(120));
        // 30 * 2^9 = 15360s > 900s cap
        assertThat(retryPolicy.nextRetryAt(now, 10, null)).isEqualTo(now.plusSeconds(900));
    }

    @Test
    void honorsProviderSuppliedRetryAfterOverBackoffFormula() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(retryPolicy.nextRetryAt(now, 1, 7)).isEqualTo(now.plusSeconds(7));
    }
}
