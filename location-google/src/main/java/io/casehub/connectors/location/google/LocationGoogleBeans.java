package io.casehub.connectors.location.google;

import io.casehub.platform.api.authn.StaticCredentialStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

public class LocationGoogleBeans {

    @Produces
    @ApplicationScoped
    GoogleLocationPlatform googleLocationPlatform(StaticCredentialStore credentialStore) {
        return new GoogleLocationPlatform(credentialStore);
    }
}
