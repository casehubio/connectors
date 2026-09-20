package io.casehub.connectors.graphql.dto;

public record OperationResult(boolean dispatched, String connector, String destination, String detail) {}
