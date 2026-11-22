package io.casehub.connectors.chat.telegram;

import java.util.List;

import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.ReceivedMessage;
import io.casehub.connectors.chat.spi.InboundTranslator;

public class TelegramInboundTranslator implements InboundTranslator {

    @Override
    public String connectorType() {
        return InboundConnectorTypes.TELEGRAM;
    }

    @Override
    public ReceivedMessage translate(final InboundMessage msg) {
        final var channel = new ChatChannelRef(msg.externalChannelRef());
        final var messageRef = new ChatMessageRef(channel,
                msg.metadata().get("telegram-message-id"));

        final String replyToId = msg.metadata().get("telegram-reply-to-message-id");
        final ChatMessageRef parentRef = replyToId != null
                ? new ChatMessageRef(channel, replyToId)
                : null;

        return new ReceivedMessage(
                InboundConnectorTypes.TELEGRAM,
                channel,
                messageRef,
                parentRef,
                new MemberRef(msg.externalSenderId()),
                new ChatContent(msg.content(), null, msg.attachments(), List.of()),
                msg.receivedAt());
    }
}
