package io.casehub.connectors.travel.model;

import java.time.LocalDate;

public record AccommodationBookingRequest(
    String accommodationId,
    String roomType,
    LocalDate checkIn,
    LocalDate checkOut,
    String guestName,
    String guestEmail
) {}
