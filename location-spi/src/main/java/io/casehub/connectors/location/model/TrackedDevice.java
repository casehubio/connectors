package io.casehub.connectors.location.model;

public record TrackedDevice(String id, String name, String type,
                            Position lastKnownPosition, BatteryInfo battery) {}
