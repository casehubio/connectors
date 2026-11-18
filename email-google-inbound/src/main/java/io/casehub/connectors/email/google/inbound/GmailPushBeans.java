package io.casehub.connectors.email.google.inbound;

import io.casehub.platform.api.authn.ServiceConnectionProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
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
    @Named("gmail-push")
    public JwtValidator gmailJwtValidator(
            @ConfigProperty(name = "casehub.connectors.gmail.push.pubsub-audience",
                    defaultValue = "") String audience) {
        if (audience == null || audience.isBlank()) {
            return jwt -> true;
        }
        return new GoogleOidcJwtValidator(audience);
    }

    @Produces
    @ApplicationScoped
    public GmailPushInboundConnector gmailPushInboundConnector(
            HistoryStore historyStore,
            GmailHistoryClient historyClient,
            @Named("gmail-push") JwtValidator jwtValidator) {
        return new GmailPushInboundConnector(historyStore, historyClient, jwtValidator);
    }
}
