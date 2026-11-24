package io.casehub.connectors.lifecycle;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectorRegistry {

    private final Map<String, RegisteredConnector> connectors = new ConcurrentHashMap<>();

    public record RegisteredConnector(ConnectorManifest manifest, ConnectorStatus status) {}

    public void register(ConnectorManifest manifest, ConnectorStatus status) {
        connectors.put(manifest.connector().id(), new RegisteredConnector(manifest, status));
    }

    public ConnectorStatus status(String connectorId) {
        var reg = connectors.get(connectorId);
        return reg != null ? reg.status() : null;
    }

    public Optional<ConnectorManifest> manifest(String connectorId) {
        var reg = connectors.get(connectorId);
        return reg != null ? Optional.of(reg.manifest()) : Optional.empty();
    }

    public List<RegisteredConnector> all() {
        return List.copyOf(connectors.values());
    }

    public List<RegisteredConnector> configured() {
        return all().stream().filter(r -> r.status() == ConnectorStatus.READY).toList();
    }

    public List<RegisteredConnector> unconfigured() {
        return all().stream().filter(r -> r.status() == ConnectorStatus.UNCONFIGURED).toList();
    }
}
