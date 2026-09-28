package io.casehub.connectors.document.google;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class DocumentGoogleBeans {

    @Produces
    @ApplicationScoped
    public GoogleDocumentPlatform googleDocumentPlatform(
            @ConfigProperty(name = "casehub.connectors.document.google.client-id", defaultValue = "") String clientId,
            @ConfigProperty(name = "casehub.connectors.document.google.client-secret", defaultValue = "") String clientSecret,
            @ConfigProperty(name = "casehub.connectors.document.google.refresh-token", defaultValue = "") String refreshToken) {
        return new GoogleDocumentPlatform(clientId, clientSecret, refreshToken);
    }
}
