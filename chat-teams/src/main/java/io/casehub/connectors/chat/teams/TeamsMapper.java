package io.casehub.connectors.chat.teams;

import io.casehub.connectors.chat.model.Channel;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.Member;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.PresenceStatus;
import io.casehub.connectors.chat.model.ReceivedMessage;
import io.casehub.connectors.chat.model.RichCard;
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
        boolean useHtml = content.markdown() != null || !content.cards().isEmpty();
        String  bodyText;
        String  contentType;
        if (content.markdown() != null) {
            bodyText    = markdownToHtml(content.markdown());
            contentType = "html";
        } else if (useHtml) {
            bodyText    = escapeHtml(content.text());
            contentType = "html";
        } else {
            bodyText    = content.text();
            contentType = "text";
        }

        var msg = Json.createObjectBuilder();

        if (!content.cards().isEmpty()) {
            var attachments = Json.createArrayBuilder();
            int idx         = 0;
            var refs        = new StringBuilder();
            for (var card : content.cards()) {
                String attachId = "card-" + idx;
                attachments.add(Json.createObjectBuilder()
                                    .add("id", attachId)
                                    .add("contentType", "application/vnd.microsoft.card.adaptive")
                                    .addNull("contentUrl")
                                    .add("content", toAdaptiveCardJson(card)));
                refs.append("<attachment id=\"").append(attachId).append("\"></attachment>");
                idx++;
            }
            msg.add("attachments", attachments);
            bodyText = bodyText + refs;
        }

        msg.add("body", Json.createObjectBuilder()
                            .add("contentType", contentType)
                            .add("content", bodyText));
        return msg.build();
    }

    static String markdownToHtml(String md) {
        String html = escapeHtml(md);
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "<em>$1</em>");
        html = html.replaceAll("`(.+?)`", "<code>$1</code>");
        html = html.replaceAll("\\[(.+?)]\\((.+?)\\)", "<a href=\"$2\">$1</a>");
        html = html.replace("\n", "<br>");
        return html;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&")
                   .replace("<", "<")
                   .replace(">", ">");
    }

    private static String toAdaptiveCardJson(RichCard card) {
        var body = Json.createArrayBuilder();
        if (card.author() != null) {
            body.add(textBlock(card.author(), null, null, true));
        }
        if (card.title() != null) {
            body.add(textBlock(card.title(), "Large", "Bolder", false));
        }
        if (card.description() != null) {
            body.add(textBlock(card.description(), null, null, false));
        }
        if (card.imageUrl() != null) {
            body.add(Json.createObjectBuilder()
                         .add("type", "Image")
                         .add("url", card.imageUrl()));
        }
        if (!card.fields().isEmpty()) {
            var facts = Json.createArrayBuilder();
            for (var field : card.fields()) {
                facts.add(Json.createObjectBuilder()
                              .add("title", field.name())
                              .add("value", field.value()));
            }
            body.add(Json.createObjectBuilder()
                         .add("type", "FactSet")
                         .add("facts", facts));
        }
        if (card.footer() != null) {
            body.add(textBlock(card.footer(), "Small", null, true));
        }

        var cardBuilder = Json.createObjectBuilder()
                              .add("$schema", "http://adaptivecards.io/schemas/adaptive-card.json")
                              .add("type", "AdaptiveCard")
                              .add("version", "1.4")
                              .add("body", body);

        if (card.url() != null) {
            cardBuilder.add("actions", Json.createArrayBuilder()
                                           .add(Json.createObjectBuilder()
                                                    .add("type", "Action.OpenUrl")
                                                    .add("title", "Open")
                                                    .add("url", card.url())));
        }

        return cardBuilder.build().toString();
    }

    private static JsonObject textBlock(String text, String size, String weight,
                                        boolean isSubtle) {
        var block = Json.createObjectBuilder()
                        .add("type", "TextBlock")
                        .add("text", text)
                        .add("wrap", true);
        if (size != null) {block.add("size", size);}
        if (weight != null) {block.add("weight", weight);}
        if (isSubtle) {block.add("isSubtle", true);}
        return block.build();
    }


    static String[] parseTeamAndChannel(String compoundId) {
        int colon = compoundId.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("Expected teamId:channelId format: " + compoundId);
        }
        return new String[]{compoundId.substring(0, colon), compoundId.substring(colon + 1)};
    }
}
