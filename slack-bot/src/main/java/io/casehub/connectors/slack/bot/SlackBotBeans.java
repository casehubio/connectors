package io.casehub.connectors.slack.bot;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class SlackBotBeans {

    @Produces
    @ApplicationScoped
    public SlackBotClient slackBotClient(
            @ConfigProperty(name = "casehub.connectors.slack-bot.api-base-url",
                            defaultValue = "https://slack.com") String apiBaseUrl) {
        return new SlackBotClient(apiBaseUrl);
    }

    @Produces
    @ApplicationScoped
    public SlackBotDiscovery slackBotDiscovery(
            SlackBotClient slackBotClient,
            @ConfigProperty(name = "casehub.connectors.slack-bot.token",
                            defaultValue = "") String botToken) {
        return new SlackBotDiscovery(slackBotClient, botToken);
    }
}
