package io.casehub.connectors.location.model;

import java.util.List;

public record Route(
    String summary,
    Distance distance,
    Duration duration,
    List<RouteLeg> legs
) {}
