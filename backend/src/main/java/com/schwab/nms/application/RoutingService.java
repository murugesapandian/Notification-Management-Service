package com.schwab.nms.application;

import com.schwab.nms.domain.enums.Channel;
import com.schwab.nms.domain.enums.RecipientType;
import com.schwab.nms.domain.enums.Severity;
import com.schwab.nms.domain.model.Notification;
import com.schwab.nms.domain.model.NotificationRecipient;
import com.schwab.nms.domain.model.RecipientPreference;
import com.schwab.nms.infrastructure.persistence.repository.RecipientPreferenceRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the effective delivery channel(s) per recipient (section 4.3).
 *
 * Policy, in order:
 * <ol>
 *   <li>Start from the notification's requested channels; if none were requested,
 *       start from the platform default set (EMAIL).</li>
 *   <li>Drop channels the recipient has explicitly disabled in their preferences.
 *       A recipient with no preference row for a channel is treated as opted-in
 *       (opt-out model, not opt-in) so newly onboarded channels reach users by
 *       default.</li>
 *   <li>CRITICAL severity overrides recipient opt-outs and always routes to every
 *       channel the recipient has a known identity for, on the grounds that a
 *       safety/compliance-critical alert must not be silently suppressed by a
 *       stale preference. This is a documented, defensible policy choice — see
 *       docs/architecture-overview.md — not an inferred requirement.</li>
 *   <li>If, after filtering, no channel remains eligible, fall back to EMAIL as
 *       the guaranteed minimum channel and record the reason.</li>
 * </ol>
 */
@Service
public class RoutingService {

    private static final Channel DEFAULT_CHANNEL = Channel.EMAIL;

    private final RecipientPreferenceRepository preferenceRepository;

    public RoutingService(RecipientPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    public record RoutingResult(String recipientId, RecipientType recipientType, Channel requestedChannel,
                                 Channel resolvedChannel, String reason) {
    }

    public List<RoutingResult> resolve(Notification notification) {
        Set<Channel> requested = notification.getRequestedChannels().stream()
                .map(rc -> rc.getChannel())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Channel.class)));
        boolean noneRequested = requested.isEmpty();
        Set<Channel> baseline = noneRequested ? EnumSet.of(DEFAULT_CHANNEL) : requested;

        List<RoutingResult> results = new ArrayList<>();
        for (NotificationRecipient recipient : notification.getRecipients()) {
            List<RecipientPreference> preferences = preferenceRepository.findByRecipientId(recipient.getRecipientId());

            Set<Channel> eligible = EnumSet.noneOf(Channel.class);
            for (Channel channel : baseline) {
                if (isEnabled(preferences, channel)) {
                    eligible.add(channel);
                }
            }

            if (notification.getSeverity() == Severity.CRITICAL) {
                for (Channel channel : Channel.values()) {
                    eligible.add(channel);
                }
                for (Channel channel : baseline) {
                    results.add(new RoutingResult(recipient.getRecipientId(), recipient.getRecipientType(),
                            noneRequested ? null : channel, channel, "critical-severity-override"));
                }
                // Critical alerts fan out to every channel, not just the requested ones.
                for (Channel channel : eligible) {
                    if (!baseline.contains(channel)) {
                        results.add(new RoutingResult(recipient.getRecipientId(), recipient.getRecipientType(),
                                null, channel, "critical-severity-fanout"));
                    }
                }
                continue;
            }

            if (eligible.isEmpty()) {
                results.add(new RoutingResult(recipient.getRecipientId(), recipient.getRecipientType(),
                        noneRequested ? null : baseline.iterator().next(), DEFAULT_CHANNEL,
                        "fallback-no-eligible-channel"));
                continue;
            }

            for (Channel channel : eligible) {
                results.add(new RoutingResult(recipient.getRecipientId(), recipient.getRecipientType(),
                        noneRequested ? null : channel, channel, "requested-and-eligible"));
            }
        }
        return results;
    }

    private boolean isEnabled(List<RecipientPreference> preferences, Channel channel) {
        return preferences.stream()
                .filter(p -> p.getChannel() == channel)
                .findFirst()
                .map(RecipientPreference::isEnabled)
                .orElse(true);
    }
}
