package io.casehub.connectors.graphql;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorService;
import io.casehub.connectors.InboundConnectorService;
import io.casehub.connectors.WebhookInboundConnector;
import io.casehub.connectors.chat.ChatPlatformService;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class GraphqlBeans {

    @Produces
    @ApplicationScoped
    public ConnectorOperationsImpl connectorOperationsImpl(
            InboundConnectorService inboundService,
            ConnectorService connectorService,
            ChatPlatformService chatPlatformService,
            @All List<Connector> connectors,
            @All List<WebhookInboundConnector> webhookConnectors,
            Instance<SentMessageCapture> sentMessageCapture,
            CurrentPrincipal currentPrincipal) {
        return new ConnectorOperationsImpl(
                inboundService,
                connectorService,
                chatPlatformService,
                connectors,
                webhookConnectors,
                sentMessageCapture.isResolvable()
                        ? Optional.of(sentMessageCapture.get())
                        : Optional.empty(),
                currentPrincipal);
    }
}
