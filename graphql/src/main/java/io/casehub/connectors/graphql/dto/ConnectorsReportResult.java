package io.casehub.connectors.graphql.dto;

import java.util.List;
import java.util.Map;

public record ConnectorsReportResult(
        Map<String, PlatformInfo> platforms,
        List<String> outbound,
        boolean simulationActive) {
}
