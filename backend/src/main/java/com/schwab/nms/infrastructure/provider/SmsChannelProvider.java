package com.schwab.nms.infrastructure.provider;

import com.schwab.nms.domain.enums.Channel;
import org.springframework.stereotype.Component;

/** Simulated SMS integration (production target: Twilio / SNS SMS). */
@Component
public class SmsChannelProvider extends AbstractSimulatedProvider {

    @Override
    public Channel supportedChannel() {
        return Channel.SMS;
    }

    @Override
    protected String providerName() {
        return "sms";
    }
}
