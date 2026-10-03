package io.casehub.connectors.location.model;

import java.util.List;

public record GeocodingResult(
    String formattedAddress,
    Coordinates location,
    String placeId,
    List<String> types
) {}
