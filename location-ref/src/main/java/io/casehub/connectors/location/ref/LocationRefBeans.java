package io.casehub.connectors.location.ref;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

public class LocationRefBeans {

    @Produces
    @ApplicationScoped
    RefLocationPlatform refLocationPlatform(LocationBackend backend) {
        return new RefLocationPlatform(backend);
    }
}
