package io.casehub.connectors.travel.ref;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class TravelRefBeans {

    @Produces
    @ApplicationScoped
    RefTravelPlatform refTravelPlatform(TravelBackend backend) {
        return new RefTravelPlatform(backend);
    }
}
