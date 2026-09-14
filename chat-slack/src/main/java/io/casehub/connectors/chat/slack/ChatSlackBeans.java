package io.casehub.connectors.chat.slack;

import io.casehub.connectors.slack.bot.SlackBotClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ChatSlackBeans {

    @Produces
    @ApplicationScoped
    public SlackChatPlatform slackChatPlatform(
            SlackBotClient client,
            @ConfigProperty(name = "casehub.slack.token", defaultValue = "") String token) {
        return new SlackChatPlatform(client, token);
    }
}
