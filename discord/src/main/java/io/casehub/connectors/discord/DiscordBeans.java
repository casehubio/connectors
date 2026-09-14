package io.casehub.connectors.discord;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class DiscordBeans {

    @Produces
    @ApplicationScoped
    public DiscordClient discordClient(
            @ConfigProperty(name = "casehub.discord.api-base-url",
                            defaultValue = "https://discord.com/api/v10") String apiBaseUrl,
            @ConfigProperty(name = "casehub.discord.attachment.allowed-cdn-hosts",
                            defaultValue = "cdn.discordapp.com,media.discordapp.net") String allowedCdnHostsConfig,
            @ConfigProperty(name = "casehub.discord.attachment.max-bytes",
                            defaultValue = "8388608") long maxAttachmentBytes) {
        return new DiscordClient(apiBaseUrl, allowedCdnHostsConfig, maxAttachmentBytes);
    }

    @Produces
    @ApplicationScoped
    public DiscordGatewayPresenceCache discordGatewayPresenceCache() {
        return new DiscordGatewayPresenceCache();
    }

    @Produces
    @ApplicationScoped
    public DiscordDiscovery discordDiscovery(
            DiscordClient client,
            @ConfigProperty(name = "casehub.discord.token",
                            defaultValue = "") String token) {
        return new DiscordDiscovery(client, token);
    }
}
