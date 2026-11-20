package io.casehub.connectors.location.model;

import io.casehub.connectors.Coordinates;
import java.time.Instant;

public record Position(Coordinates location, double accuracyMeters, Instant timestamp) {}
