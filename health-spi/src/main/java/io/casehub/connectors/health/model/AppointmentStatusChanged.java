package io.casehub.connectors.health.model;

import java.time.Instant;

public record AppointmentStatusChanged(String platformId, String appointmentId,
                                       AppointmentStatus status, String reason,
                                       String eventId, Instant timestamp) {}
