package io.casehub.connectors.commerce.ref;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class CommerceRefBeans {

    @Produces
    @ApplicationScoped
    RefCommercePlatform refCommercePlatform(CommerceBackend backend) {
        return new RefCommercePlatform(backend);
    }
}
