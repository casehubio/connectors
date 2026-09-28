package io.casehub.connectors.graphql;

import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.chat.ChatPlatformService;
import io.casehub.connectors.chat.spi.ChatPlatform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorChatApiTest {

    @Test
    void listChatChannelsThrowsWhenDiscoveryUnsupported() {
        var platform = ChatPlatform.builder("messaging-only")
                                   .messaging((ch, content) -> null)
                                   .build();
        var service = new ChatPlatformService(List.of(platform));
        var api     = new ConnectorChatApi();
        api.chatPlatformService = service;

        assertThatThrownBy(() -> api.listChatChannels("messaging-only"))
                .isInstanceOf(UnsupportedCapabilityException.class)
                .satisfies(ex -> {
                    var uce = (UnsupportedCapabilityException) ex;
                    assertThat(uce.operation()).isEqualTo("listChatChannels");
                    assertThat(uce.capability()).isEqualTo("Discovery");
                    assertThat(uce.provider()).isEqualTo("messaging-only");
                    assertThat(uce.supportedCapabilities()).contains("Messaging");
                    assertThat(uce.supportedCapabilities()).doesNotContain("Discovery");
                });
    }

    @Test
    void replyToMessageThrowsWhenThreadingUnsupported() {
        var platform = ChatPlatform.builder("minimal")
                .messaging((ch, content) -> null)
                .build();
        var service = new ChatPlatformService(List.of(platform));
        var api = new ConnectorChatApi();
        api.chatPlatformService = service;

        assertThatThrownBy(() -> api.replyToMessage("minimal", "C001", "msg1", "reply"))
                .isInstanceOf(UnsupportedCapabilityException.class)
                .satisfies(ex -> {
                    var uce = (UnsupportedCapabilityException) ex;
                    assertThat(uce.capability()).isEqualTo("Threading");
                });
    }

    @Test
    void addReactionThrowsWhenReactionsUnsupported() {
        var platform = ChatPlatform.builder("minimal")
                .messaging((ch, content) -> null)
                .build();
        var service = new ChatPlatformService(List.of(platform));
        var api = new ConnectorChatApi();
        api.chatPlatformService = service;

        assertThatThrownBy(() -> api.addReaction("minimal", "C001", "msg1", ":thumbsup:"))
                .isInstanceOf(UnsupportedCapabilityException.class)
                .satisfies(ex -> {
                    var uce = (UnsupportedCapabilityException) ex;
                    assertThat(uce.capability()).isEqualTo("Reactions");
                });
    }
}
