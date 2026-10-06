package io.casehub.connectors.travel.model;

public record Accommodation(
    String id,
    String name,
    AccommodationType type,
    String location,
    double rating,
    Money pricePerNight
) {}
