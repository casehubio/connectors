package io.casehub.connectors.health.model;

import io.casehub.connectors.Coordinates;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

public record Provider(ProviderId id, String name, ProviderType type,
                       String address, Coordinates location, String phone,
                       boolean acceptingNewPatients, List<String> specialties,
                       Map<DayOfWeek, List<TimeRange>> openingHours) {}
