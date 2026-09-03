package com.schwab.nms.infrastructure.provider;

import com.schwab.nms.domain.enums.Channel;
import org.springframework.stereotype.Component;

/**
 * Brownfield addition (docs/scenarios/02-brownfield.md): simulated Slack
 * integration (production target: the Slack Web API chat.postMessage, using an
 * incoming-webhook or bot token per workspace). Added purely by implementing
 * {@link ChannelProvider} and registering as a Spring bean — {@link ChannelProviderRegistry}
 * picks it up automatically.
 */
@Component
public class SlackChannelProvider extends AbstractSimulatedProvider {

    @Override
    public Channel supportedChannel() {
        return Channel.SLACK;
    }

    @Override
    protected String providerName() {
        return "slack";
    }
}
