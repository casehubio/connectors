package io.casehub.connectors.contacts.google;

import io.casehub.platform.api.authn.ServiceConnectionProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

public class ContactsGoogleBeans {

    @Produces
    @ApplicationScoped
    GoogleContactsPlatform googleContactsPlatform(ServiceConnectionProvider connectionProvider) {
        return new GoogleContactsPlatform(connectionProvider);
    }
}
