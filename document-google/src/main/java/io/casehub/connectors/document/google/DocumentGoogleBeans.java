package io.casehub.connectors.document.google;

import io.casehub.platform.api.authn.ServiceConnectionProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class DocumentGoogleBeans {

    @Produces
    @ApplicationScoped
    public GoogleDocumentPlatform googleDocumentPlatform(ServiceConnectionProvider connectionProvider) {
        return new GoogleDocumentPlatform(connectionProvider);
    }
}
