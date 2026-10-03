package io.casehub.connectors.location.spi;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.List;

public class LocationBeans {

    @Produces
    @ApplicationScoped
    LocationPlatformService locationPlatformService(@All List<LocationPlatform> platforms) {
        return new LocationPlatformService(platforms);
    }
}
