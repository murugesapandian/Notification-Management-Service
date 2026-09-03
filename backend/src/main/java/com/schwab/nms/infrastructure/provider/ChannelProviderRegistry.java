package com.schwab.nms.infrastructure.provider;

import com.schwab.nms.domain.enums.Channel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Brownfield refactor (docs/scenarios/02-brownfield.md): replaces the if/else
 * channel dispatch that lived in the delivery worker with a Strategy/Registry
 * lookup built from whatever {@link ChannelProvider} beans Spring finds. Adding a
 * new channel (this scenario's SLACK) is now a matter of implementing the
 * interface and registering the bean — no existing dispatch code changes,
 * satisfying the Open/Closed Principle where the original chain did not.
 */
@Component
public class ChannelProviderRegistry {

    private final Map<Channel, ChannelProvider> providersByChannel;

    public ChannelProviderRegistry(List<ChannelProvider> providers) {
        this.providersByChannel = new EnumMap<>(Channel.class);
        for (ChannelProvider provider : providers) {
            ChannelProvider previous = providersByChannel.put(provider.supportedChannel(), provider);
            if (previous != null) {
                throw new IllegalStateException("Multiple ChannelProvider beans registered for channel "
                        + provider.supportedChannel() + ": " + previous.getClass().getSimpleName()
                        + " and " + provider.getClass().getSimpleName());
            }
        }
    }

    public ChannelProvider resolve(Channel channel) {
        ChannelProvider provider = providersByChannel.get(channel);
        if (provider == null) {
            throw new IllegalStateException("No ChannelProvider registered for channel " + channel);
        }
        return provider;
    }
}
