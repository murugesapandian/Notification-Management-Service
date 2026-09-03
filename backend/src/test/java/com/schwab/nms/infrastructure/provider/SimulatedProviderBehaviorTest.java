package com.schwab.nms.infrastructure.provider;

import com.schwab.nms.domain.enums.Channel;
import com.schwab.nms.domain.enums.FailureCategory;
import com.schwab.nms.domain.enums.RecipientType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the deterministic simulation rules shared by all channel providers
 * (see AbstractSimulatedProvider javadoc) using EmailChannelProvider as the
 * representative implementation.
 */
class SimulatedProviderBehaviorTest {

    private final EmailChannelProvider provider = new EmailChannelProvider();

    private DeliveryContext ctx(String recipientId, int attemptNumber) {
        return new DeliveryContext(UUID.randomUUID(), UUID.randomUUID(), recipientId,
                RecipientType.EMAIL, "subject", "body", attemptNumber);
    }

    @Test
    void succeedsForAnOrdinaryRecipient() {
        ProviderResult result = provider.send(ctx("jane.doe@example.com", 1));
        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).startsWith("email-");
    }

    @Test
    void classifiesInvalidRecipientAsNonRetryable() {
        ProviderResult result = provider.send(ctx("invalid-user", 1));
        assertThat(result.success()).isFalse();
        assertThat(result.failureCategory()).isEqualTo(FailureCategory.INVALID_RECIPIENT);
        assertThat(result.failureCategory().isRetryable()).isFalse();
    }

    @Test
    void classifiesAuthFailureAsNonRetryable() {
        ProviderResult result = provider.send(ctx("authfail-user", 1));
        assertThat(result.failureCategory()).isEqualTo(FailureCategory.AUTH_ERROR);
    }

    @Test
    void classifiesTimeoutAsRetryable() {
        ProviderResult result = provider.send(ctx("timeout-user", 1));
        assertThat(result.failureCategory()).isEqualTo(FailureCategory.TIMEOUT);
        assertThat(result.failureCategory().isRetryable()).isTrue();
    }

    @Test
    void classifiesRateLimitWithRetryAfterHint() {
        ProviderResult result = provider.send(ctx("ratelimit-user", 1));
        assertThat(result.failureCategory()).isEqualTo(FailureCategory.RATE_LIMITED);
        assertThat(result.retryAfterSeconds()).isEqualTo(2);
    }

    @Test
    void classifiesPermanentRejectionAsNonRetryable() {
        ProviderResult result = provider.send(ctx("reject-user", 1));
        assertThat(result.failureCategory()).isEqualTo(FailureCategory.PERMANENT_PROVIDER_REJECTION);
    }

    @Test
    void flakyRecipientFailsFirstAttemptThenSucceeds() {
        ProviderResult first = provider.send(ctx("flaky-user", 1));
        ProviderResult second = provider.send(ctx("flaky-user", 2));

        assertThat(first.success()).isFalse();
        assertThat(first.failureCategory()).isEqualTo(FailureCategory.TRANSIENT_PROVIDER_FAILURE);
        assertThat(second.success()).isTrue();
    }

    @Test
    void supportedChannelMatchesProvider() {
        assertThat(provider.supportedChannel()).isEqualTo(Channel.EMAIL);
        assertThat(new SmsChannelProvider().supportedChannel()).isEqualTo(Channel.SMS);
        assertThat(new PushChannelProvider().supportedChannel()).isEqualTo(Channel.PUSH);
        assertThat(new SlackChannelProvider().supportedChannel()).isEqualTo(Channel.SLACK);
    }

    @Test
    void slackProviderFollowsTheSameSimulationRulesAsOtherChannels() {
        SlackChannelProvider slack = new SlackChannelProvider();
        ProviderResult result = slack.send(ctx("invalid-user", 1));
        assertThat(result.failureCategory()).isEqualTo(FailureCategory.INVALID_RECIPIENT);
    }
}
