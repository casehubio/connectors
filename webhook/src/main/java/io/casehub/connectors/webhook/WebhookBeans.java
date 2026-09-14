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

    @Produces
    @ApplicationScoped
    public TeamsInboundConnector teamsInboundConnector(
            @ConfigProperty(name = "casehub.connectors.teams-inbound.shared-secret", defaultValue = "") String sharedSecret) {
        return new TeamsInboundConnector(sharedSecret);
    }

    @Produces
    @ApplicationScoped
    public WhatsAppInboundConnector whatsAppInboundConnector(
            @ConfigProperty(name = "casehub.connectors.whatsapp-inbound.app-secret", defaultValue = "") String appSecret,
            @ConfigProperty(name = "casehub.connectors.whatsapp-inbound.verify-token", defaultValue = "") String verifyToken) {
        return new WhatsAppInboundConnector(appSecret, verifyToken);
    }

    @Produces
    @ApplicationScoped
    public TwilioSmsInboundConnector twilioSmsInboundConnector(
            @ConfigProperty(name = "casehub.connectors.twilio-sms-inbound.auth-token", defaultValue = "") String authToken) {
        return new TwilioSmsInboundConnector(authToken);
    }
}
