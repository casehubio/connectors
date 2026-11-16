package io.casehub.connectors.chat.teams;

import io.casehub.connectors.chat.spi.ChatPlatform;
import io.casehub.connectors.microsoft.MicrosoftGraphClient;
import io.casehub.platform.api.authn.ServiceConnectionProvider;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TeamsBeans {


    @Produces
    @ApplicationScoped
    TeamsCommands teamsCommands() {
        return new TeamsCommands();
    }

    @Produces
    @ApplicationScoped
    ChatPlatform teamsChatPlatform(MicrosoftGraphClient graph,
                                   ServiceConnectionProvider connectionProvider,
                                   TeamsCommands teamsCommands,
                                   ConversationReferenceStore conversationReferenceStore,
                                   @ConfigProperty(name = "casehub.connectors.teams.default-team-id",
                                                   defaultValue = "") String defaultTeamId,
                                   @ConfigProperty(name = "casehub.connectors.teams-bot.service-url-allowlist",
                                                   defaultValue = "*.botframework.com,smba.trafficmanager.net") String allowlist,
                                   @ConfigProperty(name = "casehub.connectors.teams-bot.default-service-url",
                                                   defaultValue = "") String defaultServiceUrl,
                                   @ConfigProperty(name = "casehub.connectors.teams-bot.default-tenant-id",
                                                   defaultValue = "") String defaultTenantId,
                                   @ConfigProperty(name = "casehub.connectors.teams-bot.app-id",
                                                   defaultValue = "") String appId) {
        var builder = new TeamsChatPlatform(new TeamsClient(graph), connectionProvider,
                                            defaultTeamId)
                              .withCommands(teamsCommands)
                              .withConversationReferenceStore(conversationReferenceStore);
        if (!defaultServiceUrl.isBlank()) {
            var botClient = new BotFrameworkClient(connectionProvider, allowlist);
            builder.withBotClient(botClient, defaultServiceUrl);
        }
        if (!defaultTenantId.isBlank() && !appId.isBlank()) {
            builder.withProactiveConfig(defaultTenantId, appId);
        }
        return builder.build();
    }

    @Produces
    @ApplicationScoped
    io.casehub.connectors.chat.spi.InboundTranslator teamsInboundTranslator() {
        return new TeamsInboundTranslator();
    }

    @Produces
    @ApplicationScoped
    io.casehub.connectors.WebhookInboundConnector teamsInboundConnector(
            @ConfigProperty(name = "casehub.connectors.teams-bot.app-id", defaultValue = "") String appId,
            @ConfigProperty(name = "casehub.connectors.teams-bot.dev-mode", defaultValue = "false") boolean devMode,
            @ConfigProperty(name = "casehub.connectors.teams-bot.service-url-allowlist",
                            defaultValue = "*.botframework.com,smba.trafficmanager.net") String allowlist,
            @ConfigProperty(name = "casehub.connectors.teams-bot.openid-metadata-url",
                            defaultValue = "https://login.botframework.com/v1/.well-known/openidconfiguration") String metadataUrl,
            @ConfigProperty(name = "casehub.connectors.teams-bot.issuer",
                            defaultValue = "https://api.botframework.com") String issuer,
            io.casehub.connectors.chat.command.CommandService commandService,
            ServiceConnectionProvider connectionProvider) {
        var auth   = new BotFrameworkAuth(appId, devMode, metadataUrl, issuer);
        var client = new BotFrameworkClient(connectionProvider, allowlist);
        return new TeamsInboundConnector(auth, client, commandService, appId);
    }

}
