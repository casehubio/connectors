package io.casehub.connectors.location.spi;

import java.util.List;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.location.model.Coordinates;
import io.casehub.connectors.location.model.GeocodingResult;
import io.casehub.connectors.location.model.Place;
import io.casehub.connectors.location.model.PlaceDetail;
import io.casehub.connectors.location.model.Route;
import io.casehub.connectors.location.model.TravelMode;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpLocationPlatform implements LocationPlatform {

    @Override
    public String id() {
        return "none";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return false;
    }

    @Override
    public PlaceSearch placeSearch(String userId) {
        return NoOpPlaceSearch.INSTANCE;
    }

    @Override
    public PlaceDetails placeDetails(String userId) {
        return NoOpPlaceDetails.INSTANCE;
    }

    @Override
    public Geocoding geocoding(String userId) {
        return NoOpGeocoding.INSTANCE;
    }

    @Override
    public Directions directions(String userId) {
        return NoOpDirections.INSTANCE;
    }

    private enum NoOpPlaceSearch implements PlaceSearch {
        INSTANCE;

        @Override
        public Page<Place> searchByText(String query, PageRequest pagination) {
            throw new UnsupportedCapabilityException("searchByText", "PlaceSearch", "none", List.of());
        }

        @Override
        public Page<Place> searchNearby(Coordinates location, int radiusMeters,
                                        PageRequest pagination) {
            throw new UnsupportedCapabilityException("searchNearby", "PlaceSearch", "none", List.of());
        }

        @Override
        public Page<Place> searchByCategory(String category, Coordinates location,
                                            int radiusMeters, PageRequest pagination) {
            throw new UnsupportedCapabilityException("searchByCategory", "PlaceSearch", "none",
                List.of());
        }
    }

    private enum NoOpPlaceDetails implements PlaceDetails {
        INSTANCE;

        @Override
        public PlaceDetail get(String placeId) {
            throw new UnsupportedCapabilityException("get", "PlaceDetails", "none", List.of());
        }
    }

    private enum NoOpGeocoding implements Geocoding {
        INSTANCE;

        @Override
        public List<GeocodingResult> geocode(String address) {
            throw new UnsupportedCapabilityException("geocode", "Geocoding", "none", List.of());
        }

        @Override
        public List<GeocodingResult> reverseGeocode(Coordinates location) {
            throw new UnsupportedCapabilityException("reverseGeocode", "Geocoding", "none",
                List.of());
        }
    }

    private enum NoOpDirections implements Directions {
        INSTANCE;

        @Override
        public Route route(Coordinates origin, Coordinates destination, TravelMode mode) {
            throw new UnsupportedCapabilityException("route", "Directions", "none", List.of());
        }
    }
}
