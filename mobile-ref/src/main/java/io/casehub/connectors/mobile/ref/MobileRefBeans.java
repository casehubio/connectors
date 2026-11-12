package io.casehub.connectors.mobile.ref;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class MobileRefBeans {

    @Produces
    @ApplicationScoped
    RefMobilePlatform refMobilePlatform(InMemoryMobileBackend backend) {
        return new RefMobilePlatform(backend);
    }
}
