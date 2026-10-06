package io.casehub.connectors.travel.model;

import java.time.LocalDate;

public record AccommodationBookingConfirmation(
    String bookingReference,
    String accommodationId,
    String roomType,
    LocalDate checkIn,
    LocalDate checkOut,
    String guestName,
    BookingStatus status,
    Money totalPrice
) {}
