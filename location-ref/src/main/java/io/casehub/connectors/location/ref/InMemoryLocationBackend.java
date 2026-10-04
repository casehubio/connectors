package io.casehub.connectors.location.ref;

import io.casehub.connectors.location.model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

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
        addPlace("The Italian Kitchen", "10 King's Cross Rd, London N1 9AA",
            51.5318, -0.1239, List.of("restaurant", "italian"),
            4.5, 320, "+442071234001", "https://italiankitchen.example.com",
            PriceLevel.MODERATE,
            new OpeningHours(List.of(
                "Mon: 11:00-22:00", "Tue: 11:00-22:00", "Wed: 11:00-22:00",
                "Thu: 11:00-23:00", "Fri: 11:00-23:00", "Sat: 10:00-23:00",
                "Sun: 10:00-21:00"), true),
            List.of(new Review("Alice S.", 5.0, "Best pasta in the area", 1727900000000L),
                    new Review("Bob J.", 4.0, "Good food, slow service", 1727800000000L)),
            List.of(new Photo("photo-ik-1", 800, 600, List.of("The Italian Kitchen"))));

        addPlace("Costa Coffee King's Cross", "King's Cross Station, London N1C 4AH",
            51.5320, -0.1240, List.of("cafe", "coffee_shop"),
            4.0, 1500, "+442071234002", "https://costa.co.uk",
            PriceLevel.INEXPENSIVE,
            new OpeningHours(List.of(
                "Mon: 06:00-21:00", "Tue: 06:00-21:00", "Wed: 06:00-21:00",
                "Thu: 06:00-21:00", "Fri: 06:00-21:00", "Sat: 07:00-20:00",
                "Sun: 08:00-19:00"), true),
            List.of(new Review("Carol S.", 4.0, "Convenient location", 1727700000000L)),
            List.of());

        addPlace("British Museum", "Great Russell St, London WC1B 3DG",
            51.5194, -0.1270, List.of("museum", "tourist_attraction"),
            4.7, 85000, "+442073231234", "https://britishmuseum.org",
            PriceLevel.FREE,
            new OpeningHours(List.of(
                "Mon: 10:00-17:00", "Tue: 10:00-17:00", "Wed: 10:00-17:00",
                "Thu: 10:00-20:30", "Fri: 10:00-17:00", "Sat: 10:00-17:00",
                "Sun: 10:00-17:00"), true),
            List.of(new Review("Dave W.", 5.0, "World-class collection", 1727600000000L)),
            List.of(new Photo("photo-bm-1", 1200, 800, List.of("British Museum"))));

        addPlace("Dishoom King's Cross", "5 Stable St, London N1C 4AB",
            51.5355, -0.1250, List.of("restaurant", "indian"),
            4.6, 12000, "+442071234004", "https://dishoom.com",
            PriceLevel.MODERATE,
            new OpeningHours(List.of(
                "Mon: 08:00-23:00", "Tue: 08:00-23:00", "Wed: 08:00-23:00",
                "Thu: 08:00-23:00", "Fri: 08:00-00:00", "Sat: 08:00-00:00",
                "Sun: 08:00-23:00"), true),
            List.of(new Review("Eve B.", 5.0, "The bacon naan is legendary", 1727500000000L),
                    new Review("Frank L.", 4.0, "Long queue but worth it", 1727400000000L)),
            List.of(new Photo("photo-dk-1", 800, 600, List.of("Dishoom"))));

        addPlace("Waterstones Piccadilly", "203-206 Piccadilly, London W1J 9HD",
            51.5085, -0.1369, List.of("book_store", "shop"),
            4.7, 4500, "+442071234005", "https://waterstones.com",
            PriceLevel.MODERATE,
            new OpeningHours(List.of(
                "Mon: 09:00-22:00", "Tue: 09:00-22:00", "Wed: 09:00-22:00",
                "Thu: 09:00-22:00", "Fri: 09:00-22:00", "Sat: 09:00-22:00",
                "Sun: 12:00-18:30"), true),
            List.of(new Review("Grace C.", 5.0, "Six floors of books!", 1727300000000L)),
            List.of());

        addPlace("The Shard", "32 London Bridge St, London SE1 9SG",
            51.5045, -0.0865, List.of("tourist_attraction", "observation_deck"),
            4.5, 35000, "+442071234006", "https://the-shard.com",
            PriceLevel.EXPENSIVE,
            new OpeningHours(List.of(
                "Mon: 10:00-22:00", "Tue: 10:00-22:00", "Wed: 10:00-22:00",
                "Thu: 10:00-22:00", "Fri: 10:00-22:00", "Sat: 10:00-22:00",
                "Sun: 10:00-22:00"), true),
            List.of(new Review("Hank M.", 4.0, "Amazing views, pricey entry", 1727200000000L)),
            List.of(new Photo("photo-ts-1", 1200, 1600, List.of("The Shard"))));

        addPlace("Borough Market", "8 Southwark St, London SE1 1TL",
            51.5055, -0.0910, List.of("market", "food_market"),
            4.6, 45000, "+442071234007", "https://boroughmarket.org.uk",
            PriceLevel.MODERATE,
            new OpeningHours(List.of(
                "Mon: Closed", "Tue: 10:00-17:00", "Wed: 10:00-17:00",
                "Thu: 10:00-17:00", "Fri: 10:00-18:00", "Sat: 08:00-17:00",
                "Sun: Closed"), false),
            List.of(new Review("Ivy D.", 5.0, "Foodie paradise", 1727100000000L)),
            List.of(new Photo("photo-bm2-1", 800, 600, List.of("Borough Market"))));

        addPlace("Tesco Express King's Cross", "1 Euston Rd, London N1 9AB",
            51.5300, -0.1230, List.of("supermarket", "grocery"),
            3.5, 200, "+442071234008", null,
            PriceLevel.INEXPENSIVE,
            new OpeningHours(List.of(
                "Mon: 06:00-23:00", "Tue: 06:00-23:00", "Wed: 06:00-23:00",
                "Thu: 06:00-23:00", "Fri: 06:00-23:00", "Sat: 07:00-22:00",
                "Sun: 08:00-22:00"), true),
            List.of(),
            List.of());

        geocodingEntries.add(new GeocodingEntry(
            "King's Cross, London", new Coordinates(51.5318, -0.1239), "place-kx",
            List.of("neighborhood", "political")));
        geocodingEntries.add(new GeocodingEntry(
            "10 King's Cross Rd, London N1 9AA", new Coordinates(51.5318, -0.1239), "p-1",
            List.of("street_address")));
        geocodingEntries.add(new GeocodingEntry(
            "British Museum, Great Russell St, London WC1B 3DG",
            new Coordinates(51.5194, -0.1270), "p-3",
            List.of("establishment", "museum")));
        geocodingEntries.add(new GeocodingEntry(
            "London Bridge, London SE1", new Coordinates(51.5055, -0.0876), "place-lb",
            List.of("neighborhood", "political")));
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
