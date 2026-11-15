package io.casehub.connectors.webhook;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class WebhookBeans {

    @Produces
    @ApplicationScoped
    public SlackInboundConnector slackInboundConnector(
            @ConfigProperty(name = "casehub.connectors.slack-inbound.signing-secret", defaultValue = "") String signingSecret) {
        return new SlackInboundConnector(signingSecret);
    }

}
