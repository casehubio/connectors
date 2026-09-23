package io.casehub.connectors.bank.truelayer;

import io.quarkus.oidc.client.NamedOidcClient;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TrueLayerBeans {

    @Inject
    ConsentTokenStore consentTokenStore;

    @Produces
    @ApplicationScoped
    public TrueLayerClient trueLayerClient(
            @NamedOidcClient("truelayer") OidcClient oidcClient,
            @ConfigProperty(name = "casehub.connectors.bank.truelayer.base-url",
                            defaultValue = "https://api.truelayer.com") String baseUrl) {
        return new TrueLayerClient(oidcClient, baseUrl);
    }

    @Produces
    @ApplicationScoped
    public TrueLayerConsentService trueLayerConsentService(
            OidcClient oidcClient,
            ConsentTokenStore consentTokenStore,
            @ConfigProperty(name = "casehub.connectors.bank.truelayer.client-id",
                            defaultValue = "") String clientId,
            @ConfigProperty(name = "casehub.connectors.bank.truelayer.client-secret",
                            defaultValue = "") String clientSecret,
            @ConfigProperty(name = "casehub.connectors.bank.truelayer.auth-url",
                            defaultValue = "https://auth.truelayer.com") String authUrl) {
        return new TrueLayerConsentService(clientId, clientSecret, authUrl,
                                           oidcClient, consentTokenStore);
    }

    @Scheduled(every = "24h")
    void cleanupExpiredConsents() {
        new ConsentCleanupJob(consentTokenStore).run();
    }
}
