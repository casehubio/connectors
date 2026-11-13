package io.casehub.connectors.spring;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMeshBridge;
import io.casehub.connectors.ConnectorService;
import io.casehub.connectors.InboundConnector;
import io.casehub.connectors.InboundConnectorService;
import io.casehub.connectors.NoOpConnectorMeshBridge;
import io.casehub.connectors.SentMessage;
import io.casehub.connectors.slack.SlackConnector;
import io.casehub.connectors.teams.TeamsConnector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@AutoConfiguration
public class ConnectorsAutoConfiguration {

    private static final Logger LOG = Logger.getLogger(ConnectorsAutoConfiguration.class.getName());

    @Bean
    public ConnectorService connectorService(List<Connector> connectors,
                                              ApplicationEventPublisher publisher) {
        return new ConnectorService(connectors,
                msg -> publisher.publishEvent(msg));
    }

    @Bean
    public InboundConnectorService inboundConnectorService(List<InboundConnector> pullConnectors,
                                                            ApplicationEventPublisher publisher) {
        return new InboundConnectorService(pullConnectors,
                msg -> publisher.publishEvent(msg));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startInboundConnectors(ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(InboundConnectorService.class).start();
    }

    @EventListener(ContextClosedEvent.class)
    public void stopInboundConnectors(ContextClosedEvent event) {
        event.getApplicationContext().getBean(InboundConnectorService.class).stop();
    }

    @Bean
    @ConditionalOnMissingBean(ConnectorMeshBridge.class)
    public ConnectorMeshBridge noOpConnectorMeshBridge() {
        return new NoOpConnectorMeshBridge();
    }

    @Bean
    public SlackConnector slackConnector() {
        return new SlackConnector();
    }

    @Bean
    public TeamsConnector teamsConnector() {
        return new TeamsConnector();
    }


}
