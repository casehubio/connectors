package io.casehub.connectors.location.spi;

import java.util.List;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.location.model.Coordinates;
import io.casehub.connectors.location.model.GeocodingResult;
import io.casehub.connectors.location.model.Place;
import io.casehub.connectors.location.model.PlaceDetail;
import io.casehub.connectors.location.model.Route;
import io.casehub.connectors.location.model.TravelMode;
import io.casehub.platform.simulation.SimulationEligible;

@SimulationEligible(name = "location-platform",
    capabilities = {"placeSearch", "placeDetails", "geocoding", "directions"})
public interface LocationPlatform {

    String id();

    boolean supports(Class<?> capability);

    PlaceSearch placeSearch(String userId);

    PlaceDetails placeDetails(String userId);

    Geocoding geocoding(String userId);

    Directions directions(String userId);

    interface PlaceSearch {

        Page<Place> searchByText(String query, PageRequest pagination);

        Page<Place> searchNearby(Coordinates location, int radiusMeters, PageRequest pagination);

        Page<Place> searchByCategory(String category, Coordinates location, int radiusMeters,
                                     PageRequest pagination);
    }

    interface PlaceDetails {

        PlaceDetail get(String placeId);
    }

    interface Geocoding {

        List<GeocodingResult> geocode(String address);

        List<GeocodingResult> reverseGeocode(Coordinates location);
    }

    interface Directions {

        Route route(Coordinates origin, Coordinates destination, TravelMode mode);
    }
}
