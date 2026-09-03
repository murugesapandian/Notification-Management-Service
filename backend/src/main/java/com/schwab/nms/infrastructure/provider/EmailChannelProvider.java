package com.schwab.nms.infrastructure.provider;

import com.schwab.nms.domain.enums.Channel;
import org.springframework.stereotype.Component;

/** Simulated email integration (production target: Amazon SES / SMTP relay). */
@Component
public class EmailChannelProvider extends AbstractSimulatedProvider {

    @Override
    public Channel supportedChannel() {
        return Channel.EMAIL;
    }

    @Override
    protected String providerName() {
        return "email";
    }
}
