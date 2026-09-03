package com.schwab.nms.infrastructure.provider;

import com.schwab.nms.domain.enums.FailureCategory;

public record ProviderResult(
        boolean success,
        String providerMessageId,
        FailureCategory failureCategory,
        String errorMessage,
        Integer retryAfterSeconds
) {
    public static ProviderResult success(String providerMessageId) {
        return new ProviderResult(true, providerMessageId, null, null, null);
    }

    public static ProviderResult failure(FailureCategory category, String errorMessage) {
        return new ProviderResult(false, null, category, errorMessage, null);
    }

    public static ProviderResult rateLimited(String errorMessage, int retryAfterSeconds) {
        return new ProviderResult(false, null, FailureCategory.RATE_LIMITED, errorMessage, retryAfterSeconds);
    }
}
