package io.casehub.connectors.health.ref;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class HealthRefBeans {

    @Produces
    @ApplicationScoped
    RefHealthPlatform refHealthPlatform(HealthBackend backend) {
        return new RefHealthPlatform(backend);
    }
}
