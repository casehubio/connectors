package io.casehub.connectors.discord;

import io.casehub.connectors.ConnectorDiscovery;
import io.casehub.connectors.DiscoveredTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.casehub.connectors.discord.model.DiscordGuild;

public class DiscordDiscovery implements ConnectorDiscovery {

    public static final String ID = "discord";

    public static final Set<Integer> TEXT_CHANNEL_TYPES = Set.of(0, 5, 10, 11, 12);

    private final DiscordClient client;
    private final String        token;

    public DiscordDiscovery(final DiscordClient client, final String token) {
        this.client = client;
        this.token  = token;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<DiscoveredTarget> discover() {
        if (token.isBlank()) {
            return List.of();
        }
        final List<DiscordGuild> guilds = client.listBotGuilds(token);
        if (guilds == null || guilds.isEmpty()) {
            return List.of();
        }
        final List<DiscoveredTarget> targets = new ArrayList<>();
        for (final DiscordGuild guild : guilds) {
            final var channels = client.listGuildChannels(token, guild.id());
            if (channels != null) {
                channels.stream()
                        .filter(ch -> TEXT_CHANNEL_TYPES.contains(ch.type()))
                        .map(ch -> new DiscoveredTarget(ch.id(), "#" + ch.name()))
                        .forEach(targets::add);
            }
        }
        return targets;
    }
}
