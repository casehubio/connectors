package io.casehub.connectors.chat.teams;

import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.ReceivedMessage;
import io.casehub.connectors.chat.spi.InboundTranslator;

public class TeamsInboundTranslator implements InboundTranslator {

    @Override
    public String connectorType() {
        return InboundConnectorTypes.TEAMS;
    }

    @Override
    public ReceivedMessage translate(InboundMessage msg) {
        String activityType = msg.metadata().get("teams-activity-type");
        if (!"message".equals(activityType)) {
            throw new IllegalArgumentException(
                    "Non-message activity type: " + activityType
                    + " — not translatable to ReceivedMessage");
        }

        var channel = new ChatChannelRef(msg.externalChannelRef());
        String activityId = msg.metadata().get("teams-activity-id");
        var messageRef = new ChatMessageRef(channel, activityId);

        String replyToId = msg.metadata().get("teams-reply-to-id");
        ChatMessageRef parentRef = replyToId != null
                ? new ChatMessageRef(channel, replyToId) : null;

        return new ReceivedMessage(
                TeamsChatPlatform.PLATFORM_ID,
                channel,
                messageRef,
                parentRef,
                new MemberRef(msg.externalSenderId()),
                new ChatContent(msg.content()),
                msg.receivedAt());
    }
}
