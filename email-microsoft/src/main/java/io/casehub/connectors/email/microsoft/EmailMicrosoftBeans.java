package io.casehub.connectors.email.microsoft;

import io.casehub.connectors.microsoft.MicrosoftGraphClient;
import io.casehub.platform.api.authn.ServiceConnectionProvider;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class EmailMicrosoftBeans {

    @Produces
    @ApplicationScoped
    MicrosoftEmailPlatform microsoftEmailPlatform(MicrosoftGraphClient graph,
            ServiceConnectionProvider connectionProvider) {
        return new MicrosoftEmailPlatform(new MicrosoftMailClient(graph),
                connectionProvider);
    }
}
