package io.casehub.connectors.travel.model;

import java.time.LocalDateTime;

public record TransportBookingConfirmation(
    String bookingReference,
    String optionId,
    String passengerName,
    BookingStatus status,
    LocalDateTime departure,
    LocalDateTime arrival,
    Money price
) {}
