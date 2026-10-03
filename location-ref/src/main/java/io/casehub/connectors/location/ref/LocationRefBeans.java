package io.casehub.connectors.location.ref;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

public class LocationRefBeans {

    @Produces
    @ApplicationScoped
    RefLocationPlatform refLocationPlatform() {
        return new RefLocationPlatform(new InMemoryLocationBackend());
    }
}
