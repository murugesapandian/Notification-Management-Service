package com.schwab.nms.config;

import com.schwab.nms.domain.enums.Channel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ambiguous-requirement scenario config (docs/scenarios/03-ambiguous-requirements.md).
 * The stakeholder ask was "make sure a critical alert doesn't get missed if nobody
 * acknowledges it" — these three values are exactly the ambiguities that had to be
 * resolved into concrete, defensible defaults: how long is "too long" to wait, and
 * who/what channel receives the escalation.
 */
@ConfigurationProperties(prefix = "nms.escalation")
public record EscalationProperties(
        long unacknowledgedCriticalThresholdMinutes,
        String escalationRecipientId,
        Channel escalationChannel
) {
}
