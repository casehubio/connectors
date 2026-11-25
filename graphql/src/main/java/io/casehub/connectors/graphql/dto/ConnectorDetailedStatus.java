package io.casehub.connectors.graphql.dto;

import io.casehub.connectors.lifecycle.ConnectorStatus;

import java.util.Map;

public record ConnectorDetailedStatus(
        String id,
        ConnectorStatus status,
        Map<String, CredentialFieldStatus> credentials,
        String healthEndpoint,
        String setupPlaybook,
        String setupStatus) {

    public record CredentialFieldStatus(
            boolean configured,
            String source,
            String defaultValue) {}
}
