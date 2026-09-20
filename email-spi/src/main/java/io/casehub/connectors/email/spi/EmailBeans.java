package io.casehub.connectors.email.spi;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.List;

@ApplicationScoped
public class EmailBeans {

    @Produces
    @ApplicationScoped
    public EmailPlatformService emailPlatformService(
            @All List<EmailPlatform> platforms) {
        return new EmailPlatformService(platforms);
    }
}
