package io.casehub.connectors.health.model;

import java.time.LocalDateTime;

public record AppointmentSlot(String slotId, ProviderId providerId,
                              LocalDateTime start, LocalDateTime end,
                              String practitionerName, String specialty) {}
