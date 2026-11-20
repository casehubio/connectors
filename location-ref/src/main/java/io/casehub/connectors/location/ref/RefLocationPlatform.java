package io.casehub.connectors.location.ref;

import io.casehub.connectors.Coordinates;
import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.PaginationHelper;
import io.casehub.connectors.location.model.GeocodingResult;
import io.casehub.connectors.location.model.Place;
import io.casehub.connectors.location.model.PlaceDetail;
import io.casehub.connectors.location.model.Position;
import io.casehub.connectors.location.model.Route;
import io.casehub.connectors.location.model.TrackedDevice;
import io.casehub.connectors.location.model.TravelMode;
import io.casehub.connectors.location.spi.LocationPlatform;

import java.util.List;
import java.util.Set;

public class RefLocationPlatform implements LocationPlatform {

    private final LocationBackend backend;

    public RefLocationPlatform(LocationBackend backend) {
        this.backend = backend;
    }

    @Override
    public String id() {
        return "ref";
    }

    private static final Set<Class<?>> SUPPORTED = Set.of(
        PlaceSearch.class, PlaceDetails.class,
        Geocoding.class, Directions.class,
        DevicePosition.class, AssetTracking.class
    );

    @Override
    public boolean supports(Class<?> capability) {
        return SUPPORTED.contains(capability);
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

    @Override
    public DevicePosition devicePosition(String userId) {
        return new RefDevicePosition();
    }

    @Override
    public AssetTracking assetTracking(String userId) {
        return new RefAssetTracking();
    }

    private class RefDevicePosition implements DevicePosition {

        @Override
        public Position currentPosition() {
            return backend.currentPosition();
        }
    }

    private class RefAssetTracking implements AssetTracking {

        @Override
        public List<TrackedDevice> trackedDevices() {
            return backend.trackedDevices();
        }

        @Override
        public Position devicePosition(String deviceId) {
            return backend.trackedDevicePosition(deviceId);
        }
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
