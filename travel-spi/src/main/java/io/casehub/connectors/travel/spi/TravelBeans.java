package io.casehub.connectors.travel.spi;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.List;

public class TravelBeans {

    @Produces
    @ApplicationScoped
    TravelPlatformService travelPlatformService(@All List<TravelPlatform> platforms) {
        return new TravelPlatformService(platforms);
    }
}
