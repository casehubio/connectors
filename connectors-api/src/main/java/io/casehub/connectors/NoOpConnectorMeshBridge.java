package io.casehub.connectors;

public class NoOpConnectorMeshBridge implements ConnectorMeshBridge {

    @Override
    public void notifyDelivered(final String connectorId,
                                final String destination,
                                final String content) {
        // intentional no-op — Qhorus bridge activates by classpath presence (qhorus#249)
    }
}
