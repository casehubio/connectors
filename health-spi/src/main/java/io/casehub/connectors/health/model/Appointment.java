package io.casehub.connectors.health.model;

import java.time.LocalDateTime;

public record Appointment(String appointmentId, ProviderId providerId,
                          String practitionerName, String specialty,
                          LocalDateTime start, LocalDateTime end,
                          String reason, AppointmentStatus status) {}
