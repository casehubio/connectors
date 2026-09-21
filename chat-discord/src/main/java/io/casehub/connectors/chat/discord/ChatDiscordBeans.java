package io.casehub.connectors.chat.discord;

import io.casehub.connectors.discord.DiscordClient;
import io.casehub.connectors.discord.DiscordGatewayPresenceCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ChatDiscordBeans {

    @Produces
    @ApplicationScoped
    public DiscordChatPlatform discordChatPlatform(
            DiscordClient client,
            DiscordGatewayPresenceCache presenceCache,
            @ConfigProperty(name = "casehub.discord.token", defaultValue = "") String token) {
        return new DiscordChatPlatform(client, presenceCache, token);
    }

    @Produces
    @ApplicationScoped
    public DiscordInboundConnector discordInboundConnector(
            DiscordClient client,
            DiscordGatewayPresenceCache presenceCache,
            @ConfigProperty(name = "casehub.discord.token", defaultValue = "") String token) {
        return new DiscordInboundConnector(client, presenceCache, token);
    }

    @Produces
    @ApplicationScoped
    public DiscordInboundTranslator discordInboundTranslator() {
        return new DiscordInboundTranslator();
    }
}
