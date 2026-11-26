package io.casehub.connectors.graphql.dto;

public record CredentialInputResult(
        String connectorId,
        String message,
        String formUrl) {}
