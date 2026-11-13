package io.casehub.connectors.chat.teams;

import io.casehub.connectors.chat.spi.ChatPlatform;
import io.casehub.connectors.microsoft.MicrosoftGraphClient;
import io.casehub.platform.api.authn.ServiceConnectionProvider;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class TeamsBeans {

    @Produces
    @ApplicationScoped
    ChatPlatform teamsChatPlatform(MicrosoftGraphClient graph,
            ServiceConnectionProvider connectionProvider) {
        return new TeamsChatPlatform(new TeamsClient(graph), connectionProvider).build();
    }
}
