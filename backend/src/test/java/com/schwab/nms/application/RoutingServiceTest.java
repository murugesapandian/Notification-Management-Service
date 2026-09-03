package com.schwab.nms.application;

import com.schwab.nms.domain.enums.Channel;
import com.schwab.nms.domain.enums.Priority;
import com.schwab.nms.domain.enums.RecipientType;
import com.schwab.nms.domain.enums.Severity;
import com.schwab.nms.domain.model.Notification;
import com.schwab.nms.domain.model.NotificationRecipient;
import com.schwab.nms.domain.model.RecipientPreference;
import com.schwab.nms.domain.model.RequestedChannel;
import com.schwab.nms.infrastructure.persistence.repository.RecipientPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

    @Mock
    private RecipientPreferenceRepository preferenceRepository;

    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        routingService = new RoutingService(preferenceRepository);
    }

    private Notification notification(Severity severity, List<Channel> requested, String... recipientIds) {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .sourceSystem("trading-platform")
                .notificationType("TRADE_ALERT")
                .severity(severity)
                .priority(Priority.NORMAL)
                .overallStatus(com.schwab.nms.domain.enums.NotificationStatus.RECEIVED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        for (String id : recipientIds) {
            notification.addRecipient(NotificationRecipient.builder()
                    .recipientId(id).recipientType(RecipientType.USER_ID).build());
        }
        for (Channel channel : requested) {
            notification.addRequestedChannel(RequestedChannel.builder().channel(channel).build());
        }
        return notification;
    }

    @Test
    void defaultsToEmailWhenNoChannelRequested() {
        when(preferenceRepository.findByRecipientId("user-1")).thenReturn(List.of());
        Notification n = notification(Severity.MEDIUM, List.of(), "user-1");

        List<RoutingService.RoutingResult> results = routingService.resolve(n);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).resolvedChannel()).isEqualTo(Channel.EMAIL);
        assertThat(results.get(0).reason()).isEqualTo("requested-and-eligible");
    }

    @Test
    void respectsRequestedChannelsWhenEligible() {
        when(preferenceRepository.findByRecipientId("user-1")).thenReturn(List.of());
        Notification n = notification(Severity.MEDIUM, List.of(Channel.SMS, Channel.PUSH), "user-1");

        List<RoutingService.RoutingResult> results = routingService.resolve(n);

        Set<Channel> resolved = results.stream().map(RoutingService.RoutingResult::resolvedChannel)
                .collect(Collectors.toSet());
        assertThat(resolved).containsExactlyInAnyOrder(Channel.SMS, Channel.PUSH);
    }

    @Test
    void dropsChannelsDisabledByRecipientPreference() {
        when(preferenceRepository.findByRecipientId("user-1")).thenReturn(List.of(
                RecipientPreference.builder().recipientId("user-1").channel(Channel.SMS).enabled(false).rank(1).build()
        ));
        Notification n = notification(Severity.MEDIUM, List.of(Channel.EMAIL, Channel.SMS), "user-1");

        List<RoutingService.RoutingResult> results = routingService.resolve(n);

        assertThat(results).extracting(RoutingService.RoutingResult::resolvedChannel)
                .containsExactly(Channel.EMAIL);
    }

    @Test
    void fallsBackToEmailWhenEverythingRequestedIsDisabled() {
        when(preferenceRepository.findByRecipientId("user-1")).thenReturn(List.of(
                RecipientPreference.builder().recipientId("user-1").channel(Channel.SMS).enabled(false).rank(1).build()
        ));
        Notification n = notification(Severity.MEDIUM, List.of(Channel.SMS), "user-1");

        List<RoutingService.RoutingResult> results = routingService.resolve(n);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).resolvedChannel()).isEqualTo(Channel.EMAIL);
        assertThat(results.get(0).reason()).isEqualTo("fallback-no-eligible-channel");
    }

    @Test
    void criticalSeverityOverridesOptOutAndFansOutToAllChannels() {
        when(preferenceRepository.findByRecipientId("user-1")).thenReturn(List.of(
                RecipientPreference.builder().recipientId("user-1").channel(Channel.SMS).enabled(false).rank(1).build()
        ));
        Notification n = notification(Severity.CRITICAL, List.of(Channel.EMAIL), "user-1");

        List<RoutingService.RoutingResult> results = routingService.resolve(n);

        Set<Channel> resolved = results.stream().map(RoutingService.RoutingResult::resolvedChannel)
                .collect(Collectors.toSet());
        assertThat(resolved).containsExactlyInAnyOrderElementsOf(Set.of(Channel.values()));
    }

    @Test
    void resolvesIndependentlyPerRecipient() {
        when(preferenceRepository.findByRecipientId(eq("user-1"))).thenReturn(List.of());
        when(preferenceRepository.findByRecipientId(eq("user-2"))).thenReturn(List.of(
                RecipientPreference.builder().recipientId("user-2").channel(Channel.EMAIL).enabled(false).rank(1).build()
        ));
        Notification n = notification(Severity.LOW, List.of(Channel.EMAIL), "user-1", "user-2");

        List<RoutingService.RoutingResult> results = routingService.resolve(n);

        assertThat(results).hasSize(2);
        assertThat(results.stream().filter(r -> r.recipientId().equals("user-1")).findFirst().get().reason())
                .isEqualTo("requested-and-eligible");
        assertThat(results.stream().filter(r -> r.recipientId().equals("user-2")).findFirst().get().reason())
                .isEqualTo("fallback-no-eligible-channel");
    }
}
