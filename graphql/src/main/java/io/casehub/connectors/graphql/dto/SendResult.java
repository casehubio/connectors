package io.casehub.connectors.graphql.dto;

public record SendResult(boolean dispatched, String connector, String destination, String detail) {}
