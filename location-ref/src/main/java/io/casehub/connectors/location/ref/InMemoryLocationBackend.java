package io.casehub.connectors.location.ref;

import io.casehub.connectors.location.model.Coordinates;
import io.casehub.connectors.location.model.Distance;
import io.casehub.connectors.location.model.Duration;
import io.casehub.connectors.location.model.GeocodingResult;
import io.casehub.connectors.location.model.OpeningHours;
import io.casehub.connectors.location.model.Photo;
import io.casehub.connectors.location.model.Place;
import io.casehub.connectors.location.model.PlaceDetail;
import io.casehub.connectors.location.model.PriceLevel;
import io.casehub.connectors.location.model.Review;
import io.casehub.connectors.location.model.Route;
import io.casehub.connectors.location.model.RouteLeg;
import io.casehub.connectors.location.model.TravelMode;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

@DefaultBean
@ApplicationScoped
public class InMemoryLocationBackend implements LocationBackend {

    private final Map<String, Place> places = new ConcurrentHashMap<>();
    private final Map<String, PlaceDetail> details = new ConcurrentHashMap<>();
    private final List<GeocodingEntry> geocodingEntries = new ArrayList<>();
    private int idSeq = 0;

    InMemoryLocationBackend() {
        seed();
    }

    private void seed() {
        SeedLoader.loadPlaces().forEach(p ->
                                                addPlace(p.name(), p.address(), p.lat(), p.lng(), p.types(),
                                                         p.rating(), p.ratingsTotal(), p.phone(), p.website(),
                                                         p.priceLevel(), p.openingHours(), p.reviews(), p.photos()));
        SeedLoader.loadGeocoding().forEach(g ->
                                                   geocodingEntries.add(new GeocodingEntry(
                                                           g.address(), new Coordinates(g.lat(), g.lng()),
                                                           g.placeId(), g.types())));
    }

    private void addPlace(String name, String address, double lat, double lng,
                          List<String> types, double rating, int ratingsTotal,
                          String phone, String website, PriceLevel priceLevel,
                          OpeningHours hours, List<Review> reviews, List<Photo> photos) {
        var id = "p-" + (++idSeq);
        var coords = new Coordinates(lat, lng);
        places.put(id, new Place(id, name, address, coords, types,
            rating, ratingsTotal, phone, website, priceLevel));
        details.put(id, new PlaceDetail(id, name, address, coords, types,
            rating, ratingsTotal, phone, phone, website, priceLevel,
            hours, reviews, photos,
            "https://maps.example.com/place/" + id));
    }

    @Override
    public List<Place> allPlaces() {
        return List.copyOf(places.values());
    }

    @Override
    public List<Place> searchByText(String query) {
        var q = query.toLowerCase();
        return places.values().stream()
            .filter(p -> matchesText(p, q))
            .toList();
    }

    @Override
    public List<Place> searchNearby(Coordinates location, int radiusMeters) {
        return places.values().stream()
            .filter(p -> distanceMeters(location, p.location()) <= radiusMeters)
            .toList();
    }

    @Override
    public List<Place> searchByCategory(String category, Coordinates location, int radiusMeters) {
        var cat = category.toLowerCase();
        return places.values().stream()
            .filter(p -> p.types().stream().anyMatch(t -> t.toLowerCase().contains(cat)))
            .filter(p -> distanceMeters(location, p.location()) <= radiusMeters)
            .toList();
    }

    @Override
    public PlaceDetail placeDetail(String placeId) {
        var detail = details.get(placeId);
        if (detail == null) throw new NoSuchElementException("Place not found: " + placeId);
        return detail;
    }

    @Override
    public List<GeocodingResult> geocode(String address) {
        var q = address.toLowerCase();
        return geocodingEntries.stream()
            .filter(e -> e.address.toLowerCase().contains(q))
            .map(e -> new GeocodingResult(e.address, e.location, e.placeId, e.types))
            .toList();
    }

    @Override
    public List<GeocodingResult> reverseGeocode(Coordinates location) {
        return geocodingEntries.stream()
            .sorted(Comparator.comparingDouble(
                e -> distanceMeters(location, e.location)))
            .limit(3)
            .map(e -> new GeocodingResult(e.address, e.location, e.placeId, e.types))
            .toList();
    }

    @Override
    public Route route(Coordinates origin, Coordinates destination, TravelMode mode) {
        long meters = (long) distanceMeters(origin, destination);
        long seconds = switch (mode) {
            case DRIVING -> meters / 10;
            case WALKING -> meters / 1;
            case BICYCLING -> meters / 4;
            case TRANSIT -> meters / 7;
        };
        var dist = new Distance(meters, formatDistance(meters));
        var dur = new Duration(seconds, formatDuration(seconds));
        var leg = new RouteLeg("Origin", "Destination", origin, destination, dist, dur);
        return new Route(mode.name().toLowerCase() + " route", dist, dur, List.of(leg));
    }

    private boolean matchesText(Place place, String query) {
        if (place.name().toLowerCase().contains(query)) return true;
        if (place.formattedAddress().toLowerCase().contains(query)) return true;
        return place.types().stream().anyMatch(t -> t.toLowerCase().contains(query));
    }

    static double distanceMeters(Coordinates a, Coordinates b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double sinLat = Math.sin(dLat / 2);
        double sinLng = Math.sin(dLng / 2);
        double x = sinLat * sinLat + Math.cos(Math.toRadians(a.lat()))
            * Math.cos(Math.toRadians(b.lat())) * sinLng * sinLng;
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x));
    }

    private static String formatDistance(long meters) {
        return meters >= 1000 ? String.format("%.1f km", meters / 1000.0)
            : meters + " m";
    }

    private static String formatDuration(long seconds) {
        if (seconds >= 3600) return String.format("%d hr %d min", seconds / 3600,
            (seconds % 3600) / 60);
        return String.format("%d min", Math.max(1, seconds / 60));
    }

    private record GeocodingEntry(String address, Coordinates location,
                                  String placeId, List<String> types) {}
}
