package io.casehub.connectors.location.google;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

public class LocationGoogleBeans {

    @Produces
    @ApplicationScoped
    GoogleLocationPlatform googleLocationPlatform(GoogleMapsKeyResolver resolver) {
        return new GoogleLocationPlatform(resolver);
    }
}
