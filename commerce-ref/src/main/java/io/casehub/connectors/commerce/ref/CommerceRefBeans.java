package io.casehub.connectors.commerce.ref;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

public class CommerceRefBeans {

    @Produces
    @ApplicationScoped
    RefCommercePlatform refCommercePlatform() {
        return new RefCommercePlatform(new InMemoryCommerceBackend());
    }
}
