package com.schwab.nms.infrastructure.provider;

import com.schwab.nms.domain.enums.FailureCategory;

import java.util.UUID;

/**
 * Shared simulation rules for the demo providers, keyed off recipientId substrings
 * so scenarios are deterministic and reproducible in tests:
 *
 * <ul>
 *   <li>"invalid-*"  -&gt; INVALID_RECIPIENT (non-retryable)</li>
 *   <li>"authfail-*" -&gt; AUTH_ERROR (non-retryable)</li>
 *   <li>"timeout-*"  -&gt; TIMEOUT (retryable, every attempt)</li>
 *   <li>"ratelimit-*"-&gt; RATE_LIMITED (retryable, every attempt)</li>
 *   <li>"flaky-*"    -&gt; TRANSIENT_PROVIDER_FAILURE on attempt 1 only, then succeeds</li>
 *   <li>"reject-*"   -&gt; PERMANENT_PROVIDER_REJECTION (non-retryable)</li>
 *   <li>anything else -&gt; success on first attempt</li>
 * </ul>
 */
public abstract class AbstractSimulatedProvider implements ChannelProvider {

    @Override
    public ProviderResult send(DeliveryContext context) {
        String id = context.recipientId() == null ? "" : context.recipientId().toLowerCase();

        if (id.startsWith("invalid-")) {
            return ProviderResult.failure(FailureCategory.INVALID_RECIPIENT,
                    "Recipient address rejected by " + providerName());
        }
        if (id.startsWith("authfail-")) {
            return ProviderResult.failure(FailureCategory.AUTH_ERROR,
                    providerName() + " credential rejected");
        }
        if (id.startsWith("timeout-")) {
            return ProviderResult.failure(FailureCategory.TIMEOUT,
                    providerName() + " request timed out");
        }
        if (id.startsWith("ratelimit-")) {
            return ProviderResult.rateLimited(providerName() + " rate limit exceeded", 2);
        }
        if (id.startsWith("reject-")) {
            return ProviderResult.failure(FailureCategory.PERMANENT_PROVIDER_REJECTION,
                    providerName() + " permanently rejected the message");
        }
        if (id.startsWith("flaky-") && context.attemptNumber() <= 1) {
            return ProviderResult.failure(FailureCategory.TRANSIENT_PROVIDER_FAILURE,
                    providerName() + " transient error, will recover");
        }

        return ProviderResult.success(providerName() + "-" + UUID.randomUUID());
    }

    protected abstract String providerName();
}
