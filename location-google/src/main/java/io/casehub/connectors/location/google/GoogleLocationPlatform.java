package io.casehub.connectors.location.google;

import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.PlaceDetailsRequest;
import com.google.maps.PlacesApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.LatLng;
import com.google.maps.model.PlacesSearchResult;
import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.location.model.*;
import io.casehub.connectors.location.spi.LocationPlatform;
import io.casehub.platform.api.authn.StaticCredentialStore;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class GoogleLocationPlatform implements LocationPlatform {

    private static final Logger LOG = Logger.getLogger(GoogleLocationPlatform.class);
    private static final String DEFAULT_TENANCY = "default";

    private final StaticCredentialStore credentialStore;
    private final ConcurrentHashMap<String, GeoApiContext> contexts = new ConcurrentHashMap<>();

    public GoogleLocationPlatform(StaticCredentialStore credentialStore) {
        this.credentialStore = credentialStore;
    }

    @Override
    public String id() {
        return "google";
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
        return new GooglePlaceSearch(contextFor(userId));
    }

    @Override
    public PlaceDetails placeDetails(String userId) {
        return new GooglePlaceDetails(contextFor(userId));
    }

    @Override
    public Geocoding geocoding(String userId) {
        return new GoogleGeocoding(contextFor(userId));
    }

    @Override
    public Directions directions(String userId) {
        return new GoogleDirections(contextFor(userId));
    }

    private GeoApiContext contextFor(String userId) {
        var record = credentialStore.find(userId, "google-maps", DEFAULT_TENANCY)
            .orElseThrow(() -> new IllegalStateException(
                "No Google Maps API key configured for user " + userId));
        return contexts.computeIfAbsent(record.credential(), key ->
            new GeoApiContext.Builder().apiKey(key).build());
    }

    private class GooglePlaceSearch implements PlaceSearch {

        private final GeoApiContext context;

        GooglePlaceSearch(GeoApiContext context) {
            this.context = context;
        }

        @Override
        public Page<Place> searchByText(String query, PageRequest pagination) {
            try {
                var request = PlacesApi.textSearchQuery(context, query);
                if (pagination.cursor() != null) {
                    request.pageToken(pagination.cursor());
                }
                var response = request.await();
                var places = mapPlaces(response.results);
                var nextCursor = response.nextPageToken;
                return new Page<>(places, nextCursor, nextCursor != null);
            } catch (ApiException | InterruptedException | IOException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                LOG.warn("Partial results — Google Places text search failed", e);
                return new Page<>(List.of(), null, false);
            }
        }

        @Override
        public Page<Place> searchNearby(Coordinates location, int radiusMeters,
                                        PageRequest pagination) {
            try {
                var request = PlacesApi.nearbySearchQuery(context,
                    new LatLng(location.lat(), location.lng()))
                    .radius(radiusMeters);
                if (pagination.cursor() != null) {
                    request.pageToken(pagination.cursor());
                }
                var response = request.await();
                var places = mapPlaces(response.results);
                var nextCursor = response.nextPageToken;
                return new Page<>(places, nextCursor, nextCursor != null);
            } catch (ApiException | InterruptedException | IOException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                LOG.warn("Partial results — Google Places nearby search failed", e);
                return new Page<>(List.of(), null, false);
            }
        }

        @Override
        public Page<Place> searchByCategory(String category, Coordinates location,
                                            int radiusMeters, PageRequest pagination) {
            try {
                var request = PlacesApi.nearbySearchQuery(context,
                    new LatLng(location.lat(), location.lng()))
                    .radius(radiusMeters)
                    .keyword(category);
                if (pagination.cursor() != null) {
                    request.pageToken(pagination.cursor());
                }
                var response = request.await();
                var places = mapPlaces(response.results);
                var nextCursor = response.nextPageToken;
                return new Page<>(places, nextCursor, nextCursor != null);
            } catch (ApiException | InterruptedException | IOException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                LOG.warn("Partial results — Google Places category search failed", e);
                return new Page<>(List.of(), null, false);
            }
        }
    }

    private class GooglePlaceDetails implements PlaceDetails {

        private final GeoApiContext context;

        GooglePlaceDetails(GeoApiContext context) {
            this.context = context;
        }

        @Override
        public PlaceDetail get(String placeId) {
            try {
                var result = PlacesApi.placeDetails(context, placeId)
                    .fields(PlaceDetailsRequest.FieldMask.NAME,
                            PlaceDetailsRequest.FieldMask.FORMATTED_ADDRESS,
                            PlaceDetailsRequest.FieldMask.GEOMETRY,
                            PlaceDetailsRequest.FieldMask.RATING,
                            PlaceDetailsRequest.FieldMask.USER_RATINGS_TOTAL,
                            PlaceDetailsRequest.FieldMask.FORMATTED_PHONE_NUMBER,
                            PlaceDetailsRequest.FieldMask.INTERNATIONAL_PHONE_NUMBER,
                            PlaceDetailsRequest.FieldMask.PRICE_LEVEL,
                            PlaceDetailsRequest.FieldMask.OPENING_HOURS,
                            PlaceDetailsRequest.FieldMask.REVIEWS,
                            PlaceDetailsRequest.FieldMask.PHOTOS,
                            PlaceDetailsRequest.FieldMask.URL)
                    .await();
                return mapPlaceDetail(placeId, result);
            } catch (ApiException | InterruptedException | IOException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new RuntimeException("Failed to get place details: " + placeId, e);
            }
        }
    }

    private class GoogleGeocoding implements Geocoding {

        private final GeoApiContext context;

        GoogleGeocoding(GeoApiContext context) {
            this.context = context;
        }

        @Override
        public List<GeocodingResult> geocode(String address) {
            try {
                var results = GeocodingApi.geocode(context, address).await();
                return Arrays.stream(results)
                    .map(GoogleLocationPlatform::mapGeocodingResult)
                    .toList();
            } catch (ApiException | InterruptedException | IOException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                LOG.warn("Partial results — Google Geocoding failed", e);
                return List.of();
            }
        }

        @Override
        public List<GeocodingResult> reverseGeocode(Coordinates location) {
            try {
                var results = GeocodingApi.reverseGeocode(context,
                    new LatLng(location.lat(), location.lng())).await();
                return Arrays.stream(results)
                    .map(GoogleLocationPlatform::mapGeocodingResult)
                    .toList();
            } catch (ApiException | InterruptedException | IOException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                LOG.warn("Partial results — Google reverse geocoding failed", e);
                return List.of();
            }
        }
    }

    private class GoogleDirections implements Directions {

        private final GeoApiContext context;

        GoogleDirections(GeoApiContext context) {
            this.context = context;
        }

        @Override
        public Route route(Coordinates origin, Coordinates destination, TravelMode mode) {
            try {
                var result = DirectionsApi.newRequest(context)
                    .origin(new LatLng(origin.lat(), origin.lng()))
                    .destination(new LatLng(destination.lat(), destination.lng()))
                    .mode(mapTravelMode(mode))
                    .await();
                return mapRoute(result);
            } catch (ApiException | InterruptedException | IOException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new RuntimeException("Failed to get directions", e);
            }
        }
    }

    static List<Place> mapPlaces(PlacesSearchResult[] results) {
        if (results == null) return List.of();
        return Arrays.stream(results)
            .map(GoogleLocationPlatform::mapPlace)
            .toList();
    }

    static Place mapPlace(PlacesSearchResult result) {
        var location = result.geometry != null && result.geometry.location != null
            ? new Coordinates(result.geometry.location.lat, result.geometry.location.lng)
            : null;
        var types = result.types != null ? Arrays.asList(result.types) : List.<String>of();
        return new Place(
            result.placeId,
            result.name,
            result.formattedAddress != null ? result.formattedAddress : result.vicinity,
            location,
            types,
            result.rating > 0 ? (double) result.rating : null,
            result.userRatingsTotal > 0 ? result.userRatingsTotal : null,
            null,
            null,
            null
        );
    }

    static PlaceDetail mapPlaceDetail(String placeId,
                                      com.google.maps.model.PlaceDetails result) {
        var location = result.geometry != null && result.geometry.location != null
            ? new Coordinates(result.geometry.location.lat, result.geometry.location.lng)
            : null;
        var types = result.types != null
            ? Arrays.stream(result.types).map(Enum::name).toList()
            : List.<String>of();
        var hours = result.openingHours != null
            ? new OpeningHours(
                result.openingHours.weekdayText != null
                    ? Arrays.asList(result.openingHours.weekdayText) : List.of(),
                result.openingHours.openNow != null && result.openingHours.openNow)
            : null;
        var reviews = result.reviews != null
            ? Arrays.stream(result.reviews)
                .map(r -> new Review(r.authorName, (double) r.rating, r.text,
                    r.time != null ? r.time.toEpochMilli() : 0))
                .toList()
            : List.<Review>of();
        var photos = result.photos != null
            ? Arrays.stream(result.photos)
                .map(p -> new Photo(p.photoReference, p.width, p.height,
                    p.htmlAttributions != null ? Arrays.asList(p.htmlAttributions)
                        : List.of()))
                .toList()
            : List.<Photo>of();
        return new PlaceDetail(
            placeId,
            result.name,
            result.formattedAddress,
            location,
            types,
            result.rating > 0 ? (double) result.rating : null,
            result.userRatingsTotal > 0 ? result.userRatingsTotal : null,
            result.internationalPhoneNumber,
            result.formattedPhoneNumber,
            result.website != null ? result.website.toString() : null,
            mapPriceLevel(result.priceLevel),
            hours,
            reviews,
            photos,
            result.url != null ? result.url.toString() : null
        );
    }

    static GeocodingResult mapGeocodingResult(
            com.google.maps.model.GeocodingResult result) {
        var location = result.geometry != null && result.geometry.location != null
            ? new Coordinates(result.geometry.location.lat, result.geometry.location.lng)
            : null;
        var types = result.types != null
            ? Arrays.stream(result.types).map(Enum::name).toList()
            : List.<String>of();
        return new GeocodingResult(result.formattedAddress, location, result.placeId, types);
    }

    static Route mapRoute(DirectionsResult result) {
        if (result.routes == null || result.routes.length == 0) {
            return new Route("No route found", new Distance(0, "0 m"),
                new Duration(0, "0 min"), List.of());
        }
        var route = result.routes[0];
        var legs = Arrays.stream(route.legs)
            .map(leg -> new RouteLeg(
                leg.startAddress,
                leg.endAddress,
                new Coordinates(leg.startLocation.lat, leg.startLocation.lng),
                new Coordinates(leg.endLocation.lat, leg.endLocation.lng),
                new Distance(leg.distance.inMeters, leg.distance.humanReadable),
                new Duration(leg.duration.inSeconds, leg.duration.humanReadable)))
            .toList();
        var totalDist = legs.stream().mapToLong(l -> l.distance().meters()).sum();
        var totalDur = legs.stream().mapToLong(l -> l.duration().seconds()).sum();
        return new Route(
            route.summary,
            new Distance(totalDist, route.legs[0].distance.humanReadable),
            new Duration(totalDur, route.legs[0].duration.humanReadable),
            legs);
    }

    private static PriceLevel mapPriceLevel(
            com.google.maps.model.PriceLevel priceLevel) {
        if (priceLevel == null) return null;
        return switch (priceLevel) {
            case FREE -> PriceLevel.FREE;
            case INEXPENSIVE -> PriceLevel.INEXPENSIVE;
            case MODERATE -> PriceLevel.MODERATE;
            case EXPENSIVE -> PriceLevel.EXPENSIVE;
            case VERY_EXPENSIVE -> PriceLevel.VERY_EXPENSIVE;
            case UNKNOWN -> null;
        };
    }

    private static com.google.maps.model.TravelMode mapTravelMode(TravelMode mode) {
        return switch (mode) {
            case DRIVING -> com.google.maps.model.TravelMode.DRIVING;
            case WALKING -> com.google.maps.model.TravelMode.WALKING;
            case BICYCLING -> com.google.maps.model.TravelMode.BICYCLING;
            case TRANSIT -> com.google.maps.model.TravelMode.TRANSIT;
        };
    }
}
