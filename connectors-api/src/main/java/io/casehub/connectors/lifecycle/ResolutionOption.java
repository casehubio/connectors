package io.casehub.connectors.lifecycle;

import java.util.List;

public sealed interface ResolutionOption {
    record ProviderOption(String connectorId, ConnectorStatus status, String setupPlaybook) implements ResolutionOption {}
    record ManualOption(String capability) implements ResolutionOption {}
    record FamilyOAuthOption(String familyId, List<String> memberConnectorIds) implements ResolutionOption {}
}
