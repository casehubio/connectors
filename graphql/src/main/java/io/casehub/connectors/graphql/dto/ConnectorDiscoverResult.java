package io.casehub.connectors.graphql.dto;

import io.casehub.connectors.lifecycle.ConnectorStatus;

import java.util.List;

public record ConnectorDiscoverResult(List<DiscoveredConnector> connectors) {

    public record DiscoveredConnector(
            String id,
            String platform,
            String description,
            ConnectorStatus status,
            String authType,
            List<String> requiredFields,
            List<String> optionalFields,
            List<String> missingFields,
            String setupPlaybook) {}
}
