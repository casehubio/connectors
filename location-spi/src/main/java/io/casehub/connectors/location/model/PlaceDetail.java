package io.casehub.connectors.location.model;

import java.util.List;

public record PlaceDetail(
    String id,
    String name,
    String formattedAddress,
    Coordinates location,
    List<String> types,
    Double rating,
    Integer userRatingsTotal,
    String phoneNumber,
    String formattedPhoneNumber,
    String website,
    PriceLevel priceLevel,
    OpeningHours openingHours,
    List<Review> reviews,
    List<Photo> photos,
    String url
) {}
