package io.casehub.connectors.contacts.spi;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.List;

public class ContactsBeans {

    @Produces
    @ApplicationScoped
    ContactsPlatformService contactsPlatformService(@All List<ContactsPlatform> platforms) {
        return new ContactsPlatformService(platforms);
    }
}
