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
    ChatPlatform teamsChatPlatform(MicrosoftGraphClient graph,
                                   ServiceConnectionProvider connectionProvider,
                                   @ConfigProperty(name = "casehub.connectors.teams.default-team-id",
                                                   defaultValue = "") String defaultTeamId) {
        return new TeamsChatPlatform(new TeamsClient(graph), connectionProvider,
                                     defaultTeamId).build();
    }
}
