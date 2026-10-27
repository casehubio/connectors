package io.casehub.connectors.health.model;

import java.time.LocalTime;

public record TimeRange(LocalTime open, LocalTime close) {}
