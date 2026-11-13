package io.casehub.connectors.chat.teams;

import io.casehub.connectors.chat.model.Channel;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.Member;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.PresenceStatus;
import io.casehub.connectors.chat.model.ReceivedMessage;
import io.casehub.connectors.chat.model.SendResult;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.time.Instant;

final class TeamsMapper {

    private TeamsMapper() {}

    static Channel toChannel(String teamId, JsonObject json) {
        String channelId = json.getString("id");
        String compoundId = teamId + ":" + channelId;
        String name = json.getString("displayName", "");
        String description = json.getString("description", null);
        String membershipType = json.getString("membershipType", "standard");
        boolean isPrivate = "private".equals(membershipType);
        return new Channel(new ChatChannelRef(compoundId), name, null, description, isPrivate);
    }

    static SendResult toSendResult(String teamId, String channelId, JsonObject json) {
        String messageId = json.getString("id");
        var ref = new ChatMessageRef(
                new ChatChannelRef(teamId + ":" + channelId), messageId);
        Instant ts = Instant.parse(json.getString("createdDateTime"));
        return SendResult.success(ref, ts);
    }

    static PresenceStatus toPresenceStatus(JsonObject json) {
        String availability = json.getString("availability", "");
        return switch (availability) {
            case "Available" -> PresenceStatus.ONLINE;
            case "Away", "BeRightBack" -> PresenceStatus.AWAY;
            case "Busy", "BusyIdle", "DoNotDisturb" -> PresenceStatus.DND;
            case "Offline" -> PresenceStatus.OFFLINE;
            default -> PresenceStatus.UNKNOWN;
        };
    }

    static Member toMember(JsonObject json) {
        String userId = json.getString("userId", json.getString("id", ""));
        String displayName = json.getString("displayName", "");
        return new Member(new MemberRef(userId), displayName);
    }

    static ReceivedMessage toReceivedMessage(String platformId, String teamId,
            String channelId, JsonObject json) {
        String messageId = json.getString("id");
        var channelRef = new ChatChannelRef(teamId + ":" + channelId);
        var messageRef = new ChatMessageRef(channelRef, messageId);

        String senderId = "unknown";
        if (json.containsKey("from") && !json.isNull("from")) {
            var from = json.getJsonObject("from");
            if (from.containsKey("user") && !from.isNull("user")) {
                senderId = from.getJsonObject("user").getString("id", "unknown");
            }
        }

        String bodyContent = "";
        if (json.containsKey("body") && !json.isNull("body")) {
            bodyContent = json.getJsonObject("body").getString("content", "");
        }

        Instant receivedAt = Instant.parse(json.getString("createdDateTime"));

        return new ReceivedMessage(
                platformId,
                channelRef,
                messageRef,
                null,
                new MemberRef(senderId),
                new ChatContent(bodyContent),
                receivedAt);
    }

    static JsonObject toMessageBody(ChatContent content) {
        String text = content.text();
        return Json.createObjectBuilder()
                .add("body", Json.createObjectBuilder()
                        .add("contentType", "text")
                        .add("content", text))
                .build();
    }

    static String[] parseTeamAndChannel(String compoundId) {
        int colon = compoundId.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("Expected teamId:channelId format: " + compoundId);
        }
        return new String[]{compoundId.substring(0, colon), compoundId.substring(colon + 1)};
    }
}
