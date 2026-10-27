package io.casehub.connectors.location.model;

import io.casehub.connectors.Coordinates;

public record RouteLeg(
    String startAddress,
    String endAddress,
    Coordinates startLocation,
    Coordinates endLocation,
    Distance distance,
    Duration duration
) {}
