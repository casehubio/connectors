package io.casehub.connectors.email.google;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class EmailGoogleBeans {

    @Produces
    @ApplicationScoped
    public GoogleEmailPlatform googleEmailPlatform(
            @ConfigProperty(name = "casehub.connectors.email.google.client-id", defaultValue = "") String clientId,
            @ConfigProperty(name = "casehub.connectors.email.google.client-secret", defaultValue = "") String clientSecret,
            @ConfigProperty(name = "casehub.connectors.email.google.refresh-token", defaultValue = "") String refreshToken) {
        return new GoogleEmailPlatform(clientId, clientSecret, refreshToken);
    }
}
