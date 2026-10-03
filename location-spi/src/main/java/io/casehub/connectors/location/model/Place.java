package io.casehub.connectors.location.model;

import java.util.List;

public record Place(
    String id,
    String name,
    String formattedAddress,
    Coordinates location,
    List<String> types,
    Double rating,
    Integer userRatingsTotal,
    String phoneNumber,
    String website,
    PriceLevel priceLevel
) {}
