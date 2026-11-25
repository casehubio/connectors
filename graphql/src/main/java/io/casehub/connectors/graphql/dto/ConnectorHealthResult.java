package io.casehub.connectors.graphql.dto;

import io.casehub.connectors.lifecycle.ConnectorStatus;

import java.util.List;

public record ConnectorHealthResult(List<ConnectorHealthEntry> connectors) {

    public record ConnectorHealthEntry(
            String id,
            ConnectorStatus status,
            String healthEndpoint) {}
}
