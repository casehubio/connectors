package io.casehub.connectors.travel.model;

import java.util.List;

public record AccommodationDetail(
    String id,
    String name,
    AccommodationType type,
    String location,
    String description,
    double rating,
    Money pricePerNight,
    List<String> amenities,
    List<String> images,
    List<RoomType> roomTypes
) {}
