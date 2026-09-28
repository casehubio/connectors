package io.casehub.connectors.graphql.dto;

import java.util.List;

public record PlatformInfo(String providerId, String status, List<String> capabilities) {
}
