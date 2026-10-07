package io.casehub.connectors.email.google;

import io.casehub.platform.api.authn.ServiceConnectionProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class EmailGoogleBeans {

    @Produces
    @ApplicationScoped
    public GoogleEmailPlatform googleEmailPlatform(ServiceConnectionProvider connectionProvider) {
        return new GoogleEmailPlatform(connectionProvider);
    }
}
