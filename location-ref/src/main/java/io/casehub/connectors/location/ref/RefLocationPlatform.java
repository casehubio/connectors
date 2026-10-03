package io.casehub.connectors.location.ref;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.location.model.*;
import io.casehub.connectors.location.spi.LocationPlatform;

import java.util.List;

public class RefLocationPlatform implements LocationPlatform {

    private final LocationBackend backend;

    public RefLocationPlatform(LocationBackend backend) {
        this.backend = backend;
    }

    @Override
    public String id() {
        return "ref";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return capability == PlaceSearch.class
            || capability == PlaceDetails.class
            || capability == Geocoding.class
            || capability == Directions.class;
    }

    @Override
    public PlaceSearch placeSearch(String userId) {
        return new RefPlaceSearch();
    }

    @Override
    public PlaceDetails placeDetails(String userId) {
        return new RefPlaceDetails();
    }

    @Override
    public Geocoding geocoding(String userId) {
        return new RefGeocoding();
    }

    @Override
    public Directions directions(String userId) {
        return new RefDirections();
    }

    private class RefPlaceSearch implements PlaceSearch {

        @Override
        public Page<Place> searchByText(String query, PageRequest pagination) {
            return paginate(backend.searchByText(query), pagination);
        }

        @Override
        public Page<Place> searchNearby(Coordinates location, int radiusMeters,
                                        PageRequest pagination) {
            return paginate(backend.searchNearby(location, radiusMeters), pagination);
        }

        @Override
        public Page<Place> searchByCategory(String category, Coordinates location,
                                            int radiusMeters, PageRequest pagination) {
            return paginate(backend.searchByCategory(category, location, radiusMeters),
                pagination);
        }
    }

    private class RefPlaceDetails implements PlaceDetails {

        @Override
        public PlaceDetail get(String placeId) {
            return backend.placeDetail(placeId);
        }
    }

    private class RefGeocoding implements Geocoding {

        @Override
        public List<GeocodingResult> geocode(String address) {
            return backend.geocode(address);
        }

        @Override
        public List<GeocodingResult> reverseGeocode(Coordinates location) {
            return backend.reverseGeocode(location);
        }
    }

    private class RefDirections implements Directions {

        @Override
        public Route route(Coordinates origin, Coordinates destination, TravelMode mode) {
            return backend.route(origin, destination, mode);
        }
    }

    private static <T> Page<T> paginate(List<T> all, PageRequest pagination) {
        int start = 0;
        if (pagination.cursor() != null) {
            start = Integer.parseInt(pagination.cursor());
        }
        int size = pagination.pageSize() > 0 ? pagination.pageSize() : 20;
        int end = Math.min(start + size, all.size());
        var items = all.subList(start, end);
        boolean hasMore = end < all.size();
        String nextCursor = hasMore ? String.valueOf(end) : null;
        return new Page<>(items, nextCursor, hasMore);
    }
}
