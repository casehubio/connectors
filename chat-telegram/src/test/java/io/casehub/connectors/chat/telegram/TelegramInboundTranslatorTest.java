package io.casehub.connectors.chat.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.chat.model.ReceivedMessage;

class TelegramInboundTranslatorTest {

    private final TelegramInboundTranslator translator = new TelegramInboundTranslator();

    @Test
    void connectorType_returns_telegram() {
        assertThat(translator.connectorType()).isEqualTo(InboundConnectorTypes.TELEGRAM);
    }

    @Test
    void translates_simple_message() {
        InboundMessage inbound = new InboundMessage(
                InboundConnectorIds.TELEGRAM_INBOUND,
                InboundConnectorTypes.TELEGRAM,
                "55", "100", "Hello",
                List.of(), Instant.now(),
                Map.of("telegram-chat-id", "100",
                       "telegram-message-id", "42",
                       "telegram-sender-id", "55",
                       "telegram-chat-type", "private"),
                null);

        ReceivedMessage msg = translator.translate(inbound);

        assertThat(msg.platformId()).isEqualTo(InboundConnectorTypes.TELEGRAM);
        assertThat(msg.channel().id()).isEqualTo("100");
        assertThat(msg.messageRef().messageId()).isEqualTo("42");
        assertThat(msg.sender().id()).isEqualTo("55");
        assertThat(msg.content().text()).isEqualTo("Hello");
        assertThat(msg.parentRef()).isNull();
    }

    @Test
    void translates_reply_with_parent_ref() {
        InboundMessage inbound = new InboundMessage(
                InboundConnectorIds.TELEGRAM_INBOUND,
                InboundConnectorTypes.TELEGRAM,
                "55", "100", "Reply",
                List.of(), Instant.now(),
                Map.of("telegram-chat-id", "100",
                       "telegram-message-id", "43",
                       "telegram-sender-id", "55",
                       "telegram-chat-type", "group",
                       "telegram-reply-to-message-id", "42"),
                null);

        ReceivedMessage msg = translator.translate(inbound);

        assertThat(msg.parentRef()).isNotNull();
        assertThat(msg.parentRef().messageId()).isEqualTo("42");
        assertThat(msg.parentRef().channel().id()).isEqualTo("100");
    }
}
