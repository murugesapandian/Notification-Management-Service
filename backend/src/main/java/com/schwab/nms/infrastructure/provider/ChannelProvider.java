package com.schwab.nms.infrastructure.provider;

import com.schwab.nms.domain.enums.Channel;

/**
 * Strategy contract for a channel-specific delivery integration. A real deployment
 * would back these with SES/SNS, Twilio, FCM/APNs, or the Slack Web API; this
 * prototype simulates provider behavior deterministically based on recipientId
 * naming conventions so retry/failure-classification paths are testable without
 * live network calls (see docs/testing-strategy.md).
 */
public interface ChannelProvider {

    Channel supportedChannel();

    ProviderResult send(DeliveryContext context);
}
