package io.casehub.connectors.location.ref;

import io.casehub.connectors.Coordinates;
import io.casehub.connectors.location.model.GeocodingResult;
import io.casehub.connectors.location.model.Place;
import io.casehub.connectors.location.model.PlaceDetail;
import io.casehub.connectors.location.model.Position;
import io.casehub.connectors.location.model.Route;
import io.casehub.connectors.location.model.TrackedDevice;
import io.casehub.connectors.location.model.TravelMode;

import java.util.List;

public interface LocationBackend {

    List<Place> allPlaces();

    List<Place> searchByText(String query);

    List<Place> searchNearby(Coordinates location, int radiusMeters);

    List<Place> searchByCategory(String category, Coordinates location, int radiusMeters);

    PlaceDetail placeDetail(String placeId);

    List<GeocodingResult> geocode(String address);

    List<GeocodingResult> reverseGeocode(Coordinates location);

    Route route(Coordinates origin, Coordinates destination, TravelMode mode);

    Position currentPosition();

    List<TrackedDevice> trackedDevices();

    Position trackedDevicePosition(String deviceId);

}
