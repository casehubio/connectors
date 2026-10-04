package io.casehub.connectors.location.ref;

import io.casehub.connectors.Page;
import io.casehub.connectors.PaginationHelper;
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
            return PaginationHelper.paginate(backend.searchByText(query), pagination);
        }

        @Override
        public Page<Place> searchNearby(Coordinates location, int radiusMeters,
                                        PageRequest pagination) {
            return PaginationHelper.paginate(backend.searchNearby(location, radiusMeters), pagination);
        }

        @Override
        public Page<Place> searchByCategory(String category, Coordinates location,
                                            int radiusMeters, PageRequest pagination) {
            return PaginationHelper.paginate(backend.searchByCategory(category, location, radiusMeters),
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

}
