package io.casehub.connectors;

import io.casehub.connectors.slack.SlackConnector;
import io.casehub.connectors.teams.TeamsConnector;
import io.casehub.connectors.twilio.TwilioSmsConnector;
import io.casehub.connectors.whatsapp.WhatsAppConnector;
import io.quarkus.arc.All;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class ConnectorsBeans {

    private static final Logger LOG = Logger.getLogger(ConnectorsBeans.class.getName());

    @Produces
    @ApplicationScoped
    public ConnectorService connectorService(@All List<Connector> connectors,
                                              Event<SentMessage> sentMessageEvent) {
        return new ConnectorService(connectors,
                msg -> sentMessageEvent.fireAsync(msg)
                        .exceptionally(ex -> {
                            LOG.log(Level.SEVERE, "Async SentMessage dispatch failed", ex);
                            return null;
                        }));
    }

    @Produces
    @ApplicationScoped
    public InboundConnectorService inboundConnectorService(@All List<InboundConnector> pullConnectors,
                                                            Event<InboundMessage> messageEvent) {
        return new InboundConnectorService(pullConnectors,
                msg -> messageEvent.fireAsync(msg)
                        .exceptionally(ex -> {
                            LOG.log(Level.SEVERE, "Async InboundMessage dispatch failed", ex);
                            return null;
                        }));
    }

    void onStart(@Observes StartupEvent ignored, InboundConnectorService service) {
        service.start();
    }

    void onStop(@Observes ShutdownEvent ignored, InboundConnectorService service) {
        service.stop();
    }

    @Produces
    @DefaultBean
    @Unremovable
    @ApplicationScoped
    public ConnectorMeshBridge noOpConnectorMeshBridge() {
        return new NoOpConnectorMeshBridge();
    }

    @Produces
    @ApplicationScoped
    public SlackConnector slackConnector() {
        return new SlackConnector();
    }

    @Produces
    @ApplicationScoped
    public TeamsConnector teamsConnector() {
        return new TeamsConnector();
    }

    @Produces
    @ApplicationScoped
    public WhatsAppConnector whatsAppConnector(
            @ConfigProperty(name = "casehub.connectors.whatsapp.api-token", defaultValue = "") String apiToken,
            @ConfigProperty(name = "casehub.connectors.whatsapp.phone-number-id", defaultValue = "") String phoneNumberId) {
        return new WhatsAppConnector(apiToken, phoneNumberId);
    }

    @Produces
    @ApplicationScoped
    public TwilioSmsConnector twilioSmsConnector(
            @ConfigProperty(name = "casehub.connectors.twilio.account-sid", defaultValue = "") String accountSid,
            @ConfigProperty(name = "casehub.connectors.twilio.auth-token", defaultValue = "") String authToken,
            @ConfigProperty(name = "casehub.connectors.twilio.from", defaultValue = "") String from) {
        return new TwilioSmsConnector(accountSid, authToken, from);
    }
}
