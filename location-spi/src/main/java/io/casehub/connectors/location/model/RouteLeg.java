package io.casehub.connectors.location.model;

public record RouteLeg(
    String startAddress,
    String endAddress,
    Coordinates startLocation,
    Coordinates endLocation,
    Distance distance,
    Duration duration
) {}
