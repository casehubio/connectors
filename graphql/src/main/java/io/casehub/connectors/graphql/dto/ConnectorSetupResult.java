package io.casehub.connectors.graphql.dto;

import java.util.List;

public record ConnectorSetupResult(
        String connectorId,
        boolean success,
        boolean dryRun,
        String message,
        List<String> missingFields) {}
