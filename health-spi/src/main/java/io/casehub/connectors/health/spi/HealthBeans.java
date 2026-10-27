package io.casehub.connectors.health.spi;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.List;

public class HealthBeans {

    @Produces
    @ApplicationScoped
    HealthPlatformService healthPlatformService(@All List<HealthPlatform> platforms) {
        return new HealthPlatformService(platforms);
    }
}
