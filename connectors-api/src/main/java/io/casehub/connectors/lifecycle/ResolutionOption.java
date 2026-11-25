package io.casehub.connectors.lifecycle;

public sealed interface ResolutionOption {
    record ProviderOption(String connectorId, ConnectorStatus status, String setupPlaybook) implements ResolutionOption {}
    record ManualOption(String capability) implements ResolutionOption {}
}
