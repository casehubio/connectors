package io.casehub.connectors.chat;

import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.SendResult;
import io.casehub.connectors.chat.spi.Discovery;
import io.casehub.connectors.chat.spi.Members;
import io.casehub.connectors.chat.spi.Messaging;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpChatPlatformTest {

    private final NoOpChatPlatform noop = new NoOpChatPlatform();

    @Test
    void id_returnsNone() {
        assertThat(noop.id()).isEqualTo("none");
    }

    @Test
    void messaging_returnsFailing() {
        SendResult result = noop.messaging().send(
                new ChatChannelRef("ch-1"),
                new ChatContent("hello"));
        assertThat(result.ok()).isFalse();
    }

    @Test
    void discovery_returnsEmpty() {
        assertThat(noop.discovery().listChannels()).isEmpty();
    }

    @Test
    void members_returnsEmpty() {
        assertThat(noop.members().list(new ChatChannelRef("ch-1"))).isEmpty();
    }

    @Test
    void supports_alwaysFalse() {
        assertThat(noop.supports(Messaging.class)).isFalse();
        assertThat(noop.supports(Discovery.class)).isFalse();
        assertThat(noop.supports(Members.class)).isFalse();
    }

    @Test
    void allCapabilities_areNotNull() {
        assertThat(noop.messaging()).isNotNull();
        assertThat(noop.threading()).isNotNull();
        assertThat(noop.discovery()).isNotNull();
        assertThat(noop.reactions()).isNotNull();
        assertThat(noop.presence()).isNotNull();
        assertThat(noop.members()).isNotNull();
        assertThat(noop.channelManagement()).isNotNull();
        assertThat(noop.memberManagement()).isNotNull();
        assertThat(noop.messageHistory()).isNotNull();
    }
}
