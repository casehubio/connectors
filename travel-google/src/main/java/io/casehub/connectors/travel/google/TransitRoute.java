package io.casehub.connectors.travel.google;

import io.casehub.connectors.travel.model.Money;
import io.casehub.connectors.travel.model.TransportMode;

import java.time.LocalDateTime;

public record TransitRoute(
        String id,
        String origin,
        String destination,
        TransportMode mode,
        String operator,
        LocalDateTime departure,
        LocalDateTime arrival,
        int durationMinutes,
        Money fare
) {}
