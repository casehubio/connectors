package io.casehub.connectors.graphql;

import io.casehub.connectors.ConnectorDiscovery;
import io.casehub.connectors.ConnectorMeshBridge;
import io.casehub.connectors.DiscoveredTarget;
import io.casehub.connectors.chat.ChatPlatformService;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.graphql.dto.ChannelInfo;
import io.casehub.connectors.graphql.dto.SendResult;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

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
    public SendResult sendChat(String platform, String channel, String text) {
        if (!chatPlatformService.supports(platform)) {
            return new SendResult(false, platform, channel,
                "Unknown platform. Available: " + chatPlatformService.ids());
        }
        try {
            var p = chatPlatformService.platform(platform);
            p.messaging().send(new ChatChannelRef(channel), new ChatContent(text));
            meshBridge.notifyDelivered(platform, channel, sanitize(text));
            return new SendResult(true, platform, channel, "Sent");
        } catch (Exception e) {
            return new SendResult(false, platform, channel, e.getMessage());
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

    private static String sanitize(String text) {
        if (text == null) return "";
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
}
