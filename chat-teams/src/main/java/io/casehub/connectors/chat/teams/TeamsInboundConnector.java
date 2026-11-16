package io.casehub.connectors.chat.teams;

import io.casehub.connectors.HttpMethod;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.WebhookInboundConnector;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;
import io.casehub.connectors.chat.command.CommandInvocation;
import io.casehub.connectors.chat.command.CommandResponse;
import io.casehub.connectors.chat.command.CommandService;
import io.casehub.connectors.chat.command.DeferredReply;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TeamsInboundConnector extends WebhookInboundConnector {

    private static final Logger LOG = Logger.getLogger(TeamsInboundConnector.class.getName());

    private final BotFrameworkAuth auth;
    private final BotFrameworkClient client;
    private final CommandService commandService;
    private final String appId;
    private final ConversationReferenceStore conversationReferenceStore;
    private final InvokeService invokeService;


    TeamsInboundConnector(BotFrameworkAuth auth, BotFrameworkClient client,
                          CommandService commandService, String appId) {this(auth, client, commandService, appId, null, null);}

    TeamsInboundConnector(BotFrameworkAuth auth, BotFrameworkClient client,
                          CommandService commandService, String appId,
                          ConversationReferenceStore conversationReferenceStore) {
        this(auth, client, commandService, appId, conversationReferenceStore, null);
    }

    TeamsInboundConnector(BotFrameworkAuth auth, BotFrameworkClient client,
                          CommandService commandService, String appId,
                          ConversationReferenceStore conversationReferenceStore,
                          InvokeService invokeService) {
        this.auth                       = auth;
        this.client                     = client;
        this.commandService             = commandService;
        this.appId                      = appId;
        this.conversationReferenceStore = conversationReferenceStore;
        this.invokeService              = invokeService;
    }


    @Override
    public String id() {
        return InboundConnectorIds.TEAMS_INBOUND;
    }

    @Override
    public WebhookResult handle(WebhookRequest request) {
        try {
            return doHandle(request);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "teams-inbound: unexpected error", e);
            return new WebhookResult.Ignored();
        }
    }

    private WebhookResult doHandle(WebhookRequest request) {
        if (request.method() == HttpMethod.GET) {
            return new WebhookResult.Ignored();
        }

        JsonObject activity = Json.createReader(new StringReader(request.body())).readObject();

        String serviceUrl = activity.getString("serviceUrl", "");
        if (!auth.validate(request.header("authorization"), serviceUrl)) {
            return new WebhookResult.Unauthorized();
        }

        String fromId = "";
        if (activity.containsKey("from") && !activity.isNull("from")) {
            var from = activity.getJsonObject("from");
            fromId = from.getString("id", "");
        }
        if (!appId.isBlank() && appId.equals(fromId)) {
            return new WebhookResult.Ignored();
        }

        String type = activity.getString("type", "");
        return switch (type) {
            case "message" -> handleMessage(activity);
            case "messageReaction" -> handleMessageReaction(activity);
            case "conversationUpdate" -> handleConversationUpdate(activity);
            case "invoke" -> handleInvoke(activity);
            default -> {
                LOG.fine("teams-inbound: ignoring activity type: " + type);
                yield new WebhookResult.Ignored();
            }
        };
    }

    private WebhookResult handleMessage(JsonObject activity) {
        String text = activity.getString("text", null);
        if (text == null) {
            text = "";
        } else {
            text = stripBotMention(text, activity);
        }

        String commandName = matchCommand(text);
        if (commandName != null) {
            return dispatchCommand(commandName, text, activity);
        }

        return new WebhookResult.Delivered(List.of(toInboundMessage(activity, text)));
    }

    private WebhookResult handleMessageReaction(JsonObject activity) {
        List<InboundMessage> messages = new ArrayList<>();
        String replyToId = activity.getString("replyToId", "");

        if (activity.containsKey("reactionsAdded")) {
            JsonArray added = activity.getJsonArray("reactionsAdded");
            for (JsonValue v : added) {
                JsonObject reaction = v.asJsonObject();
                messages.add(toReactionMessage(activity, reaction.getString("type", ""),
                        "added", replyToId));
            }
        }
        if (activity.containsKey("reactionsRemoved")) {
            JsonArray removed = activity.getJsonArray("reactionsRemoved");
            for (JsonValue v : removed) {
                JsonObject reaction = v.asJsonObject();
                messages.add(toReactionMessage(activity, reaction.getString("type", ""),
                        "removed", replyToId));
            }
        }

        if (messages.isEmpty()) {
            return new WebhookResult.Ignored();
        }
        return new WebhookResult.Delivered(messages);
    }

    private WebhookResult handleConversationUpdate(JsonObject activity) {
        String serviceUrl     = activity.getString("serviceUrl", "");
        String conversationId = extractConversationId(activity);
        String tenantId       = extractTenancyId(activity);

        var membersAdded = activity.containsKey("membersAdded")
                           ? activity.getJsonArray("membersAdded") : null;
        var membersRemoved = activity.containsKey("membersRemoved")
                             ? activity.getJsonArray("membersRemoved") : null;

        var addedIds = new ArrayList<String>();
        if (membersAdded != null) {
            for (var member : membersAdded) {
                var    memberObj   = member.asJsonObject();
                String aadObjectId = memberObj.getString("aadObjectId", "");
                if (!aadObjectId.isBlank() && !isBotMember(memberObj)) {
                    if (conversationReferenceStore != null) {
                        try {
                            conversationReferenceStore.store(
                                    new ConversationReference(aadObjectId, conversationId, serviceUrl, tenantId));
                        } catch (Exception e) {
                            LOG.log(Level.WARNING, "teams-inbound: failed to store conversation reference for " + aadObjectId, e);
                        }
                    }
                    addedIds.add(aadObjectId);
                }
            }
        }

        var removedIds = new ArrayList<String>();
        if (membersRemoved != null) {
            for (var member : membersRemoved) {
                var    memberObj   = member.asJsonObject();
                String aadObjectId = memberObj.getString("aadObjectId", "");
                if (!aadObjectId.isBlank() && !isBotMember(memberObj)) {
                    removedIds.add(aadObjectId);
                }
            }
        }

        var metadata = buildBaseMetadata(activity);
        metadata.put("teams-activity-type", "conversationUpdate");
        if (!addedIds.isEmpty()) {
            metadata.put("teams-members-added", String.join(",", addedIds));
        }
        if (!removedIds.isEmpty()) {
            metadata.put("teams-members-removed", String.join(",", removedIds));
        }
        String fromName = extractFromName(activity);
        if (fromName != null) {
            metadata.put("teams-from-name", fromName);
        }

        String  timestamp = activity.getString("timestamp", null);
        Instant ts        = timestamp != null ? Instant.parse(timestamp) : Instant.now();

        var msg = new InboundMessage(
                InboundConnectorIds.TEAMS_INBOUND,
                InboundConnectorTypes.TEAMS,
                extractSenderId(activity),
                extractChannelRef(activity),
                "conversationUpdate",
                List.of(),
                ts,
                metadata,
                tenantId
        );
        return new WebhookResult.Delivered(List.of(msg));
    }

    private WebhookResult handleInvoke(JsonObject activity) {
        String invokeName = activity.getString("name", "");
        JsonObject value = activity.containsKey("value") && !activity.isNull("value")
                           ? activity.getJsonObject("value")
                           : Json.createObjectBuilder().build();

        Map<String, String> metadata = buildBaseMetadata(activity);
        metadata.put("teams-activity-type", "invoke");
        metadata.put("teams-invoke-name", invokeName);
        metadata.put("teams-invoke-data", value.toString());
        String fromName = extractFromName(activity);
        if (fromName != null) {
            metadata.put("teams-from-name", fromName);
        }

        String timestamp = activity.getString("timestamp", null);
        Instant ts = timestamp != null ? Instant.parse(timestamp) : Instant.now();

        var msg = new InboundMessage(
                InboundConnectorIds.TEAMS_INBOUND,
                InboundConnectorTypes.TEAMS,
                extractSenderId(activity),
                extractChannelRef(activity),
                "",
                List.of(),
                ts,
                metadata,
                extractTenancyId(activity));

        InvokeResponse response;
        if (invokeService != null) {
            var request = new InvokeRequest(invokeName, value,
                    extractSenderId(activity), extractChannelRef(activity), metadata);
            response = invokeService.dispatch(request);
        } else {
            response = new InvokeResponse.Accepted();
        }

        String responseJson;
        try {
            responseJson = InvokeResponseSerializer.toJson(response);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "teams-invoke: response serialization failed for: " + invokeName, e);
            responseJson = InvokeResponseSerializer.toJson(new InvokeResponse.Accepted());
        }
        return new WebhookResult.Responded(responseJson, "application/json", List.of(msg));
    }

    private boolean isBotMember(JsonObject member) {
        String id = member.getString("id", "");
        return !appId.isBlank() && (id.equals("28:" + appId) || id.equals(appId));
    }


    private String stripBotMention(String text, JsonObject activity) {
        if (!activity.containsKey("entities")) return text;
        JsonArray entities = activity.getJsonArray("entities");
        for (JsonValue v : entities) {
            JsonObject entity = v.asJsonObject();
            if ("mention".equals(entity.getString("type", ""))
                    && entity.containsKey("mentioned")) {
                JsonObject mentioned = entity.getJsonObject("mentioned");
                if (appId.equals(mentioned.getString("id", ""))) {
                    String mentionText = entity.getString("text", "");
                    if (!mentionText.isEmpty()) {
                        text = text.replace(mentionText, "").trim();
                    }
                    break;
                }
            }
        }
        return text;
    }

    private String matchCommand(String text) {
        if (text.isBlank()) return null;
        String firstWord = text.split("\\s+", 2)[0];
        for (var def : commandService.definitions()) {
            if (def.name().equalsIgnoreCase(firstWord)) {
                return def.name();
            }
        }
        return null;
    }

    private WebhookResult dispatchCommand(String commandName, String text, JsonObject activity) {
        String remaining = text.length() > commandName.length()
                ? text.substring(commandName.length()).trim() : "";
        Map<String, String> arguments = remaining.isEmpty()
                ? Map.of() : Map.of("text", remaining);

        String userId = extractSenderId(activity);
        String channelRef = extractChannelRef(activity);
        Map<String, String> meta = Map.of(
                "teams-activity-id", activity.getString("id", ""),
                "teams-service-url", activity.getString("serviceUrl", ""),
                "teams-conversation-id", extractConversationId(activity));

        var invocation = new CommandInvocation(commandName, arguments, userId,
                channelRef, "microsoft-teams", meta);
        CommandResponse response = commandService.dispatch(commandName, invocation);

        String serviceUrl = activity.getString("serviceUrl", "");
        String conversationId = extractConversationId(activity);
        String activityId = activity.getString("id", "");

        return switch (response) {
            case CommandResponse.Immediate imm -> {
                client.replyToActivity(serviceUrl, conversationId, activityId, imm.text());
                yield new WebhookResult.Ignored();
            }
            case CommandResponse.Deferred def -> {
                Thread.ofVirtual().name("teams-deferred-cmd").start(() -> {
                    DeferredReply reply = (replyText, ephemeral) ->
                            client.replyToActivity(serviceUrl, conversationId,
                                    activityId, replyText);
                    def.callback().accept(reply);
                });
                yield new WebhookResult.Ignored();
            }
        };
    }

    private InboundMessage toInboundMessage(JsonObject activity, String content) {
        Map<String, String> metadata  = buildBaseMetadata(activity);
        String              replyToId = activity.getString("replyToId", null);
        if (replyToId != null) {
            metadata.put("teams-reply-to-id", replyToId);
        }
        String fromName = extractFromName(activity);
        if (fromName != null) {
            metadata.put("teams-from-name", fromName);
        }

        List<io.casehub.connectors.Attachment> attachments = downloadAttachments(activity);

        return new InboundMessage(
                InboundConnectorIds.TEAMS_INBOUND,
                InboundConnectorTypes.TEAMS,
                extractSenderId(activity),
                extractChannelRef(activity),
                content,
                attachments,
                Instant.parse(activity.getString("timestamp")),
                metadata,
                extractTenancyId(activity));
    }

    private List<io.casehub.connectors.Attachment> downloadAttachments(JsonObject activity) {
        if (!activity.containsKey("attachments")) {
            return List.of();
        }
        JsonArray attachmentArray = activity.getJsonArray("attachments");
        if (attachmentArray.isEmpty()) {
            return List.of();
        }
        List<io.casehub.connectors.Attachment> result = new ArrayList<>();
        for (JsonValue v : attachmentArray) {
            JsonObject att        = v.asJsonObject();
            String     contentUrl = att.getString("contentUrl", null);
            if (contentUrl == null) {continue;}
            String contentType = att.getString("contentType", "application/octet-stream");
            String name        = att.getString("name", "attachment");
            var    downloaded  = client.downloadAttachment(contentUrl, contentType, name);
            if (downloaded != null) {
                result.add(downloaded);
            }
        }
        return List.copyOf(result);
    }


    private InboundMessage toReactionMessage(JsonObject activity, String reactionType,
                                              String action, String replyToId) {
        Map<String, String> metadata = buildBaseMetadata(activity);
        metadata.put("teams-reaction-type", reactionType);
        metadata.put("teams-reaction-action", action);
        metadata.put("teams-reaction-message-id", replyToId);

        return new InboundMessage(
                InboundConnectorIds.TEAMS_INBOUND,
                InboundConnectorTypes.TEAMS,
                extractSenderId(activity),
                extractChannelRef(activity),
                "",
                List.of(),
                Instant.parse(activity.getString("timestamp")),
                metadata,
                extractTenancyId(activity));
    }

    private Map<String, String> buildBaseMetadata(JsonObject activity) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("teams-activity-id", activity.getString("id", ""));
        metadata.put("teams-activity-type", activity.getString("type", ""));
        metadata.put("teams-service-url", activity.getString("serviceUrl", ""));
        metadata.put("teams-conversation-id", extractConversationId(activity));
        metadata.put("teams-conversation-type", extractConversationType(activity));
        return metadata;
    }

    private String extractSenderId(JsonObject activity) {
        if (!activity.containsKey("from") || activity.isNull("from")) return "unknown";
        var from = activity.getJsonObject("from");
        String aadId = from.getString("aadObjectId", null);
        return aadId != null ? aadId : from.getString("id", "unknown");
    }

    private String extractFromName(JsonObject activity) {
        if (!activity.containsKey("from") || activity.isNull("from")) return null;
        var from = activity.getJsonObject("from");
        return from.containsKey("name") ? from.getString("name") : null;
    }

    private String extractChannelRef(JsonObject activity) {
        if (!activity.containsKey("channelData") || activity.isNull("channelData")) {
            return "personal:" + extractConversationId(activity);
        }
        var channelData = activity.getJsonObject("channelData");
        if (channelData.containsKey("team") && channelData.containsKey("channel")) {
            String teamId = channelData.getJsonObject("team").getString("id", "");
            String channelId = channelData.getJsonObject("channel").getString("id", "");
            return teamId + ":" + channelId;
        }
        return "personal:" + extractConversationId(activity);
    }

    private String extractConversationId(JsonObject activity) {
        if (!activity.containsKey("conversation") || activity.isNull("conversation")) {
            return "";
        }
        return activity.getJsonObject("conversation").getString("id", "");
    }

    private String extractConversationType(JsonObject activity) {
        if (!activity.containsKey("conversation") || activity.isNull("conversation")) {
            return "";
        }
        return activity.getJsonObject("conversation").getString("conversationType", "");
    }

    private String extractTenancyId(JsonObject activity) {
        if (!activity.containsKey("channelData") || activity.isNull("channelData")) {
            return null;
        }
        var channelData = activity.getJsonObject("channelData");
        if (channelData.containsKey("tenant") && !channelData.isNull("tenant")) {
            return channelData.getJsonObject("tenant").getString("id", null);
        }
        return null;
    }
}
