package io.casehub.connectors.location.ref;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.connectors.location.model.OpeningHours;
import io.casehub.connectors.location.model.Photo;
import io.casehub.connectors.location.model.PriceLevel;
import io.casehub.connectors.location.model.Review;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

final class SeedLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule());

    record PlaceSeed(String name, String address, double lat, double lng,
                     List<String> types, double rating, int ratingsTotal,
                     String phone, String website, PriceLevel priceLevel,
                     OpeningHours openingHours, List<Review> reviews,
                     List<Photo> photos) {}

    record GeocodingSeed(String address, double lat, double lng,
                         String placeId, List<String> types) {}

    static List<PlaceSeed> loadPlaces() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/places.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/places.yaml");
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<GeocodingSeed> loadGeocoding() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/geocoding.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/geocoding.yaml");
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SeedLoader() {}
}
