package io.casehub.connectors.email.google.inbound;

import io.casehub.platform.api.authn.ServiceConnectionProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class GmailPushBeans {

    @Produces
    @ApplicationScoped
    public GmailHistoryClient gmailHistoryClient(ServiceConnectionProvider connectionProvider) {
        return new GmailHistoryClient(connectionProvider);
    }

    @Produces
    @ApplicationScoped
    public GmailPushInboundConnector gmailPushInboundConnector(
            HistoryStore historyStore,
            GmailHistoryClient historyClient,
            @ConfigProperty(name = "casehub.connectors.gmail.push.pubsub-audience",
                    defaultValue = "") String audience) {
        return new GmailPushInboundConnector(historyStore, historyClient, audience);
    }
}
