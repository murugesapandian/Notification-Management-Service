package com.schwab.nms.infrastructure.provider;

import com.schwab.nms.domain.enums.Channel;
import org.springframework.stereotype.Component;

/** Simulated mobile push integration (production target: FCM / APNs). */
@Component
public class PushChannelProvider extends AbstractSimulatedProvider {

    @Override
    public Channel supportedChannel() {
        return Channel.PUSH;
    }

    @Override
    protected String providerName() {
        return "push";
    }
}
