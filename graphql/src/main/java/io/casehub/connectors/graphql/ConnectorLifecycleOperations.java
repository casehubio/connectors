package io.casehub.connectors.graphql;

import io.casehub.connectors.graphql.dto.ConnectorDetailedStatus;
import io.casehub.connectors.graphql.dto.ConnectorDiscoverResult;
import io.casehub.connectors.graphql.dto.ConnectorHealthResult;
import io.casehub.connectors.graphql.dto.ConnectorSetupResult;
import io.casehub.connectors.lifecycle.ConnectorManifest.FieldDeclaration;
import io.casehub.connectors.lifecycle.ConnectorRegistry;
import io.casehub.connectors.lifecycle.ConnectorRegistry.RegisteredConnector;
import io.casehub.connectors.lifecycle.ConnectorStatus;
import io.casehub.connectors.lifecycle.InMemoryCredentialFacade;
import io.casehub.connectors.lifecycle.ProviderCredentialFacade;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@McpDomain(value = "connectors/lifecycle", app = "connectors",
        summary = "Connector lifecycle — discovery, configuration status, setup, health")
public class ConnectorLifecycleOperations {

    private final ConnectorRegistry registry;
    private final ProviderCredentialFacade credentialFacade;

    @Inject
    public ConnectorLifecycleOperations(Instance<ConnectorRegistry> registryInstance,
                                        Instance<ProviderCredentialFacade> credentialFacadeInstance) {
        this(registryInstance.isResolvable() ? registryInstance.get() : new ConnectorRegistry(),
             credentialFacadeInstance.isResolvable()
                     ? credentialFacadeInstance.get()
                     : new InMemoryCredentialFacade(Map.of()));
    }

    ConnectorLifecycleOperations(ConnectorRegistry registry,
                                 ProviderCredentialFacade credentialFacade) {
        this.registry = registry;
        this.credentialFacade = credentialFacade;
    }

    @PlatformQuery("List all manifest-discovered connectors with auth requirements and configuration status")
    public ConnectorDiscoverResult connectorDiscover(String platform) {
        var all = registry.all().stream()
                .filter(r -> platform == null || r.manifest().connector().platform().equals(platform))
                .map(this::toDiscoveredConnector)
                .toList();
        return new ConnectorDiscoverResult(all);
    }

    @PlatformQuery("Detailed credential and health status for a specific connector or all connectors")
    public List<ConnectorDetailedStatus> connectorStatus(String connectorId) {
        if (connectorId != null) {
            var manifest = registry.manifest(connectorId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown connector '" + connectorId + "'. Available: "
                            + registry.all().stream().map(r -> r.manifest().connector().id()).toList()));
            var status = registry.status(connectorId);
            return List.of(toDetailedStatus(connectorId, manifest, status));
        }
        return registry.all().stream()
                .map(r -> toDetailedStatus(
                        r.manifest().connector().id(), r.manifest(), r.status()))
                .toList();
    }

    @PlatformMutation("Trigger setup playbook or validate credentials for a connector")
    public ConnectorSetupResult connectorSetup(String connectorId, boolean dryRun) {
        var manifest = registry.manifest(connectorId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown connector '" + connectorId + "'"));

        var fields = manifest.connector().auth() != null
                ? manifest.connector().auth().fields() : List.<FieldDeclaration>of();
        var missing = fields.stream()
                .filter(FieldDeclaration::required)
                .filter(f -> credentialFacade.resolve(connectorId, f.name()).isEmpty())
                .map(FieldDeclaration::name)
                .toList();

        if (!missing.isEmpty()) {
            return new ConnectorSetupResult(connectorId, false, dryRun,
                    "Missing required credentials: " + missing, missing);
        }

        return new ConnectorSetupResult(connectorId, true, dryRun,
                "All required credentials configured", List.of());
    }

    @PlatformQuery("Health check summary across all configured connectors")
    public ConnectorHealthResult connectorHealth() {
        var entries = registry.configured().stream()
                .map(this::toHealthEntry)
                .toList();
        return new ConnectorHealthResult(entries);
    }

    private ConnectorDiscoverResult.DiscoveredConnector toDiscoveredConnector(RegisteredConnector reg) {
        var decl = reg.manifest().connector();
        var fields = decl.auth() != null ? decl.auth().fields() : List.<FieldDeclaration>of();
        var connectorId = decl.id();

        var required = fields.stream().filter(FieldDeclaration::required).map(FieldDeclaration::name).toList();
        var optional = fields.stream().filter(f -> !f.required()).map(FieldDeclaration::name).toList();
        var missing = fields.stream()
                .filter(FieldDeclaration::required)
                .filter(f -> credentialFacade.resolve(connectorId, f.name()).isEmpty())
                .map(FieldDeclaration::name)
                .toList();

        return new ConnectorDiscoverResult.DiscoveredConnector(
                connectorId, decl.platform(), decl.description(),
                reg.status(),
                decl.auth() != null ? decl.auth().type() : null,
                required, optional, missing,
                decl.setupPlaybook());
    }

    private ConnectorDetailedStatus toDetailedStatus(
            String connectorId,
            io.casehub.connectors.lifecycle.ConnectorManifest manifest,
            ConnectorStatus status) {
        var decl = manifest.connector();
        var fields = decl.auth() != null ? decl.auth().fields() : List.<FieldDeclaration>of();

        var credentials = new LinkedHashMap<String, ConnectorDetailedStatus.CredentialFieldStatus>();
        for (var field : fields) {
            var configured = credentialFacade.resolve(connectorId, field.name()).isPresent();
            var source = credentialFacade.source(connectorId, field.name()).name();
            credentials.put(field.name(),
                    new ConnectorDetailedStatus.CredentialFieldStatus(
                            configured, source, field.defaultValue()));
        }

        var healthEndpoint = decl.health() != null ? decl.health().endpoint() : null;

        return new ConnectorDetailedStatus(
                connectorId, status, credentials,
                healthEndpoint, decl.setupPlaybook(), "NOT_RUN");
    }

    private ConnectorHealthResult.ConnectorHealthEntry toHealthEntry(RegisteredConnector reg) {
        var decl = reg.manifest().connector();
        var healthEndpoint = decl.health() != null ? decl.health().endpoint() : null;
        return new ConnectorHealthResult.ConnectorHealthEntry(
                decl.id(), reg.status(), healthEndpoint);
    }
}
