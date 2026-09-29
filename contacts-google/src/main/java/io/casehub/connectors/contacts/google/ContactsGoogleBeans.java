package io.casehub.connectors.contacts.google;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

public class ContactsGoogleBeans {

    @Produces
    @ApplicationScoped
    GoogleContactsPlatform googleContactsPlatform(GoogleCredentialResolver resolver) {
        return new GoogleContactsPlatform(resolver);
    }
}
