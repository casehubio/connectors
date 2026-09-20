package io.casehub.connectors.graphql;

import io.casehub.connectors.ConnectorDiscovery;
import io.casehub.connectors.ConnectorMeshBridge;
import io.casehub.connectors.DiscoveredTarget;
import io.casehub.connectors.chat.ChatPlatformService;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.graphql.dto.ChannelInfo;
import io.casehub.connectors.graphql.dto.OperationResult;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@McpDomain(value = "connectors/chat", basePath = "/api/connectors/chat")
@ApplicationScoped
public class ConnectorChatApi {

    @Inject ChatPlatformService chatPlatformService;
    @Inject ConnectorMeshBridge meshBridge;
    @Inject @Any Instance<ConnectorDiscovery> discoveryProviders;

    @PlatformMutation("Send a message to a chat platform channel")
    @RestPath("/send")
    public OperationResult sendChat(String platform, String channel, String text) {
        if (!chatPlatformService.supports(platform)) {
            return new OperationResult(false, platform, channel,
                "Unknown platform. Available: " + chatPlatformService.ids());
        }
        try {
            var p = chatPlatformService.platform(platform);
            p.messaging().send(new ChatChannelRef(channel), new ChatContent(text));
            meshBridge.notifyDelivered(platform, channel, sanitize(text));
            return new OperationResult(true, platform, channel, "Sent");
        } catch (Exception e) {
            return new OperationResult(false, platform, channel, e.getMessage());
        }
    }

    @PlatformQuery("List channels available on a chat platform")
    @RestPath("/channels")
    public List<ChannelInfo> listChatChannels(String platform) {
        if (!chatPlatformService.supports(platform)) {
            return List.of();
        }
        try {
            var p = chatPlatformService.platform(platform);
            return p.discovery().listChannels().stream()
                .map(ch -> new ChannelInfo(platform, ch.ref().id(), ch.name(),
                    ch.topic() != null ? ch.topic() : ""))
                .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    @PlatformQuery("Discover channels across all connectors")
    @RestPath("/discover")
    public List<ChannelInfo> listChannels() {
        var result = new ArrayList<ChannelInfo>();
        for (ConnectorDiscovery provider : discoveryProviders) {
            try {
                for (DiscoveredTarget target : provider.discover()) {
                    result.add(new ChannelInfo(provider.id(),
                        target.id(), target.displayName(), ""));
                }
            } catch (Exception e) {
                // skip failed providers
            }
        }
        return result;
    }

    @PlatformMutation("Reply to a message in a thread")
    @RestPath("/reply")
    public io.casehub.connectors.chat.model.SendResult replyToMessage(
            String platform, String channel, String parentMessageId, String text) {
        if (!chatPlatformService.supports(platform))
            return new io.casehub.connectors.chat.model.SendResult(false, null, null, "Unknown platform");
        try {
            var p = chatPlatformService.platform(platform);
            var parent = new io.casehub.connectors.chat.model.ChatMessageRef(
                new ChatChannelRef(channel), parentMessageId);
            return p.threading().reply(parent, new ChatContent(text));
        } catch (Exception e) {
            return new io.casehub.connectors.chat.model.SendResult(false, null, null, e.getMessage());
        }
    }

    @PlatformMutation("Add an emoji reaction to a message")
    @RestPath("/reactions/add")
    public OperationResult addReaction(String platform, String channel, String messageId, String emoji) {
        if (!chatPlatformService.supports(platform))
            return new OperationResult(false, platform, channel, "Unknown platform");
        try {
            var p = chatPlatformService.platform(platform);
            var ref = new io.casehub.connectors.chat.model.ChatMessageRef(
                new ChatChannelRef(channel), messageId);
            p.reactions().add(ref, emoji);
            return new OperationResult(true, platform, channel, "Reaction added");
        } catch (Exception e) {
            return new OperationResult(false, platform, channel, e.getMessage());
        }
    }

    @PlatformMutation("Remove an emoji reaction from a message")
    @RestPath("/reactions/remove")
    public OperationResult removeReaction(String platform, String channel, String messageId, String emoji) {
        if (!chatPlatformService.supports(platform))
            return new OperationResult(false, platform, channel, "Unknown platform");
        try {
            var p = chatPlatformService.platform(platform);
            var ref = new io.casehub.connectors.chat.model.ChatMessageRef(
                new ChatChannelRef(channel), messageId);
            p.reactions().remove(ref, emoji);
            return new OperationResult(true, platform, channel, "Reaction removed");
        } catch (Exception e) {
            return new OperationResult(false, platform, channel, e.getMessage());
        }
    }

    @PlatformQuery("List reactions on a message")
    @RestPath("/reactions")
    public List<String> listReactions(String platform, String channel, String messageId) {
        if (!chatPlatformService.supports(platform)) return List.of();
        try {
            var p = chatPlatformService.platform(platform);
            var ref = new io.casehub.connectors.chat.model.ChatMessageRef(
                new ChatChannelRef(channel), messageId);
            return p.reactions().list(ref);
        } catch (Exception e) { return List.of(); }
    }

    @PlatformQuery("Get presence status of a member")
    @RestPath("/presence")
    public String getPresence(String platform, String memberId) {
        if (!chatPlatformService.supports(platform)) return "UNKNOWN";
        try {
            var p = chatPlatformService.platform(platform);
            return p.presence().of(new io.casehub.connectors.chat.model.MemberRef(memberId)).name();
        } catch (Exception e) { return "UNKNOWN"; }
    }

    @PlatformQuery("List members of a channel")
    @RestPath("/members")
    public List<io.casehub.connectors.chat.model.Member> listMembers(String platform, String channel) {
        if (!chatPlatformService.supports(platform)) return List.of();
        try {
            var p = chatPlatformService.platform(platform);
            return p.members().list(new ChatChannelRef(channel));
        } catch (Exception e) { return List.of(); }
    }

    @PlatformMutation("Create a channel on a chat platform")
    @RestPath("/channels/create")
    public ChannelInfo createChannel(String platform, String name, String topic,
                                      String description, Boolean isPrivate) {
        if (!chatPlatformService.supports(platform)) return null;
        try {
            var p = chatPlatformService.platform(platform);
            var ch = p.channelManagement().create(name, topic, description,
                isPrivate != null && isPrivate);
            return new ChannelInfo(platform, ch.ref().id(), ch.name(),
                ch.topic() != null ? ch.topic() : "");
        } catch (Exception e) { return null; }
    }

    @PlatformMutation("Delete a channel on a chat platform")
    @RestPath("/channels/delete")
    public OperationResult deleteChannel(String platform, String channelId) {
        if (!chatPlatformService.supports(platform))
            return new OperationResult(false, platform, channelId, "Unknown platform");
        try {
            var p = chatPlatformService.platform(platform);
            p.channelManagement().delete(channelId);
            return new OperationResult(true, platform, channelId, "Deleted");
        } catch (Exception e) {
            return new OperationResult(false, platform, channelId, e.getMessage());
        }
    }

    @PlatformQuery("Get message history for a channel")
    @RestPath("/history")
    public List<io.casehub.connectors.chat.model.ReceivedMessage> messageHistory(
            String platform, String channel, @QueryParam("since") Instant since) {
        if (!chatPlatformService.supports(platform)) return List.of();
        try {
            var p = chatPlatformService.platform(platform);
            Instant effectiveSince = since != null ? since : Instant.now().minusSeconds(86400);
            return p.messageHistory().messages(new ChatChannelRef(channel), effectiveSince);
        } catch (Exception e) { return List.of(); }
    }

    private static String sanitize(String text) {
        if (text == null) return "";
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
}
