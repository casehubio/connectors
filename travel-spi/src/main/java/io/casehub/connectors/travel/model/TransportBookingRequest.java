package io.casehub.connectors.travel.model;

public record TransportBookingRequest(
    String optionId,
    String passengerName,
    String passengerEmail
) {}
