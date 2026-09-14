package io.casehub.connectors.slack.bot;

import java.util.List;

import io.casehub.connectors.ConnectorDiscovery;
import io.casehub.connectors.DiscoveredTarget;

/**
 * Implements {@link ConnectorDiscovery} for Slack via the Slack Web API.
 *
 * <p>Holds the MCP-deployment-specific bot token — kept separate from
 * {@link SlackBotClient} so the shared HTTP client is not contaminated with
 * config that is irrelevant to Qhorus consumers.
 */
public class SlackBotDiscovery implements ConnectorDiscovery {

    private final SlackBotClient slackBotClient;
    private final String botToken;

    public SlackBotDiscovery(final SlackBotClient slackBotClient, final String botToken) {
        this.slackBotClient = slackBotClient;
        this.botToken = botToken;
    }

    @Override
    public String id() {
        return SlackBotClient.ID;
    }

    @Override
    public List<DiscoveredTarget> discover() {
        if (botToken.isBlank()) return List.of();
        return slackBotClient.listChannels(botToken);
    }
}
