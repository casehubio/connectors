package io.casehub.connectors.lifecycle;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectorRegistry {

    private final Map<String, RegisteredConnector> connectors = new ConcurrentHashMap<>();
    private final ProviderCredentialFacade credentialFacade;

    public record RegisteredConnector(ConnectorManifest manifest, ConnectorStatus status) {}

    public ConnectorRegistry(ProviderCredentialFacade credentialFacade) {
        this.credentialFacade = credentialFacade;
    }

    public ConnectorRegistry() {
        this.credentialFacade = null;
    }

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

    public void updateStatus(String connectorId) {
        if (credentialFacade == null) return;
        connectors.computeIfPresent(connectorId, (id, reg) -> {
            var decl = reg.manifest().connector();
            var requiredFields = requiredFieldNames(decl);
            var configured = credentialFacade.isConfigured(decl.credentialKey(), requiredFields);
            var newStatus = configured ? ConnectorStatus.READY : ConnectorStatus.UNCONFIGURED;
            return new RegisteredConnector(reg.manifest(), newStatus);
        });
    }

    public void updateStatusByCredentialKey(String credentialKey) {
        connectors.forEach((id, reg) -> {
            if (credentialKey.equals(reg.manifest().connector().credentialKey())) {
                updateStatus(id);
            }
        });
    }

    private static List<String> requiredFieldNames(ConnectorManifest.ConnectorDeclaration decl) {
        if (decl.auth() == null || decl.auth().fields() == null) return List.of();
        return decl.auth().fields().stream()
                .filter(ConnectorManifest.FieldDeclaration::isRequired)
                .map(ConnectorManifest.FieldDeclaration::name)
                .toList();
    }
}
