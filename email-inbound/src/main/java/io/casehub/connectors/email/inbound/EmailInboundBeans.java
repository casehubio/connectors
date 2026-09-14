package io.casehub.connectors.email.inbound;

import java.util.Optional;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class EmailInboundBeans {

    @Produces
    @DefaultBean
    @ApplicationScoped
    public EmailInboundAccountProvider defaultEmailInboundAccountProvider(
            @ConfigProperty(name = "casehub.connectors.email-inbound.host", defaultValue = "") String host,
            @ConfigProperty(name = "casehub.connectors.email-inbound.port", defaultValue = "993") int port,
            @ConfigProperty(name = "casehub.connectors.email-inbound.tls", defaultValue = "true") boolean tls,
            @ConfigProperty(name = "casehub.connectors.email-inbound.username", defaultValue = "") String username,
            @ConfigProperty(name = "casehub.connectors.email-inbound.password", defaultValue = "") String password,
            @ConfigProperty(name = "casehub.connectors.email-inbound.folder", defaultValue = "INBOX") String folder,
            @ConfigProperty(name = "casehub.connectors.email-inbound.reconnect-delay-seconds", defaultValue = "60") int reconnectDelaySeconds,
            @ConfigProperty(name = "casehub.connectors.email-inbound.tenancy-id") Optional<String> tenancyId) {
        return new DefaultEmailInboundAccountProvider(host, port, tls, username, password,
                folder, reconnectDelaySeconds, tenancyId.orElse(null));
    }

    @Produces
    @ApplicationScoped
    public EmailInboundConnector emailInboundConnector(final EmailInboundAccountProvider provider) {
        return new EmailInboundConnector(provider);
    }
}
