package io.casehub.connectors.travel.model;

import java.time.LocalDateTime;

public record TransportOption(
    String id,
    String origin,
    String destination,
    TransportMode mode,
    String operator,
    LocalDateTime departure,
    LocalDateTime arrival,
    int durationMinutes,
    Money price,
    int availableSeats
) {}
