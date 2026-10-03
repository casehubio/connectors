package io.casehub.connectors.commerce.spi;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.List;

public class CommerceBeans {

    @Produces
    @ApplicationScoped
    CommercePlatformService commercePlatformService(@All List<CommercePlatform> platforms) {
        return new CommercePlatformService(platforms);
    }
}
