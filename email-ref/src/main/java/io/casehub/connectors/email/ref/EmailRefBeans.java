package io.casehub.connectors.email.ref;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class EmailRefBeans {

    @Produces
    @ApplicationScoped
    public RefEmailPlatform refEmailPlatform(EmailBackend backend) {
        return new RefEmailPlatform(backend);
    }
}
