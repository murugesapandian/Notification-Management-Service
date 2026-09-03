package com.schwab.nms.infrastructure.provider;

import com.schwab.nms.domain.enums.Channel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelProviderRegistryTest {

    @Test
    void resolvesEachRegisteredChannelToItsProvider() {
        ChannelProviderRegistry registry = new ChannelProviderRegistry(List.of(
                new EmailChannelProvider(), new SmsChannelProvider(), new PushChannelProvider(),
                new SlackChannelProvider()));

        assertThat(registry.resolve(Channel.EMAIL)).isInstanceOf(EmailChannelProvider.class);
        assertThat(registry.resolve(Channel.SMS)).isInstanceOf(SmsChannelProvider.class);
        assertThat(registry.resolve(Channel.PUSH)).isInstanceOf(PushChannelProvider.class);
        assertThat(registry.resolve(Channel.SLACK)).isInstanceOf(SlackChannelProvider.class);
    }

    @Test
    void throwsForAChannelWithNoRegisteredProvider() {
        ChannelProviderRegistry registry = new ChannelProviderRegistry(List.of(new EmailChannelProvider()));

        assertThatThrownBy(() -> registry.resolve(Channel.SLACK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SLACK");
    }

    @Test
    void rejectsTwoProvidersRegisteredForTheSameChannelAtStartup() {
        assertThatThrownBy(() -> new ChannelProviderRegistry(List.of(
                new EmailChannelProvider(), new EmailChannelProvider())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMAIL");
    }
}
