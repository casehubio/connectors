package io.casehub.connectors.graphql;

import io.casehub.connectors.Coordinates;
import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.location.model.Distance;
import io.casehub.connectors.location.model.Duration;
import io.casehub.connectors.location.model.GeocodingResult;
import io.casehub.connectors.location.model.Place;
import io.casehub.connectors.location.model.PlaceDetail;
import io.casehub.connectors.location.model.PriceLevel;
import io.casehub.connectors.location.model.Route;
import io.casehub.connectors.location.model.RouteLeg;
import io.casehub.connectors.location.model.TravelMode;
import io.casehub.connectors.location.spi.LocationPlatform;
import io.casehub.connectors.location.spi.LocationPlatformService;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectorLocationApiTest {

    @Test
    void searchPlacesThrowsWhenPlaceSearchUnsupported() {
        var platform = new StubLocationPlatform("limited", false, false, false, false);
        var api = apiWith(new LocationPlatformService(List.of(platform)));

        assertThatThrownBy(() -> api.searchPlaces("limited", "coffee", null, null))
                .isInstanceOf(UnsupportedCapabilityException.class)
                .satisfies(ex -> {
                    var uce = (UnsupportedCapabilityException) ex;
                    assertThat(uce.operation()).isEqualTo("searchPlaces");
                    assertThat(uce.capability()).isEqualTo("PlaceSearch");
                    assertThat(uce.provider()).isEqualTo("limited");
                    assertThat(uce.supportedCapabilities()).isEmpty();
                });
    }

    @Test
    void placeDetailsThrowsWhenPlaceDetailsUnsupported() {
        var platform = new StubLocationPlatform("search-only", true, false, false, false);
        var api = apiWith(new LocationPlatformService(List.of(platform)));

        assertThatThrownBy(() -> api.placeDetails("search-only", "p-1"))
                .isInstanceOf(UnsupportedCapabilityException.class)
                .satisfies(ex -> {
                    var uce = (UnsupportedCapabilityException) ex;
                    assertThat(uce.operation()).isEqualTo("placeDetails");
                    assertThat(uce.capability()).isEqualTo("PlaceDetails");
                    assertThat(uce.supportedCapabilities()).containsExactly("PlaceSearch");
                });
    }

    @Test
    void geocodeThrowsWhenGeocodingUnsupported() {
        var platform = new StubLocationPlatform("no-geo", true, true, false, false);
        var api = apiWith(new LocationPlatformService(List.of(platform)));

        assertThatThrownBy(() -> api.geocode("no-geo", "London"))
                .isInstanceOf(UnsupportedCapabilityException.class)
                .satisfies(ex -> {
                    var uce = (UnsupportedCapabilityException) ex;
                    assertThat(uce.operation()).isEqualTo("geocode");
                    assertThat(uce.capability()).isEqualTo("Geocoding");
                    assertThat(uce.supportedCapabilities())
                            .containsExactlyInAnyOrder("PlaceSearch", "PlaceDetails");
                });
    }

    @Test
    void directionsThrowsWhenDirectionsUnsupported() {
        var platform = new StubLocationPlatform("no-dir", true, true, true, false);
        var api = apiWith(new LocationPlatformService(List.of(platform)));

        assertThatThrownBy(() -> api.directions("no-dir", 51.5, -0.1, 51.4, -0.15, null))
                .isInstanceOf(UnsupportedCapabilityException.class)
                .satisfies(ex -> {
                    var uce = (UnsupportedCapabilityException) ex;
                    assertThat(uce.operation()).isEqualTo("directions");
                    assertThat(uce.capability()).isEqualTo("Directions");
                    assertThat(uce.supportedCapabilities())
                            .containsExactlyInAnyOrder("PlaceSearch", "PlaceDetails", "Geocoding");
                });
    }

    @Test
    void searchPlacesDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.searchPlaces("full-location", "coffee", null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().name()).isEqualTo("Test Coffee Shop");
    }

    @Test
    void searchNearbyDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.searchNearby("full-location", 51.5, -0.1, 50000, null, null);

        assertThat(result.items()).hasSize(1);
    }

    @Test
    void searchByCategoryDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.searchByCategory("full-location", "cafe", 51.5, -0.1, 50000, null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().types()).contains("cafe");
    }

    @Test
    void placeDetailsDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var detail = api.placeDetails("full-location", "p-1");

        assertThat(detail).isNotNull();
        assertThat(detail.name()).isEqualTo("Test Coffee Shop");
    }

    @Test
    void geocodeDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var results = api.geocode("full-location", "Test Road");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().formattedAddress()).isEqualTo("1 Test Road, London");
    }

    @Test
    void reverseGeocodeDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var results = api.reverseGeocode("full-location", 51.5, -0.1);

        assertThat(results).hasSize(1);
    }

    @Test
    void directionsDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var route = api.directions("full-location", 51.5, -0.1, 51.4, -0.15, "DRIVING");

        assertThat(route).isNotNull();
        assertThat(route.legs()).hasSize(1);
        assertThat(route.distance().meters()).isGreaterThan(0);
    }

    private ConnectorLocationApi apiWith(LocationPlatformService service) {
        var identity = mock(SecurityIdentity.class);
        var principal = mock(Principal.class);
        when(identity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("test-user");

        var api = new ConnectorLocationApi();
        api.locationService = service;
        api.identity = identity;
        return api;
    }

    private LocationPlatformService fullService() {
        return new LocationPlatformService(List.of(new FullStubLocationPlatform()));
    }

    record StubLocationPlatform(String id, boolean pSearch, boolean pDetails,
                                 boolean geocoding, boolean dir) implements LocationPlatform {
        @Override public boolean supports(Class<?> c) {
            if (c == PlaceSearch.class) return pSearch;
            if (c == PlaceDetails.class) return pDetails;
            if (c == Geocoding.class) return geocoding;
            if (c == Directions.class) return dir;
            return false;
        }
        @Override public PlaceSearch placeSearch(String u) { throw new UnsupportedOperationException(); }
        @Override public PlaceDetails placeDetails(String u) { throw new UnsupportedOperationException(); }
        @Override public Geocoding geocoding(String u) { throw new UnsupportedOperationException(); }
        @Override public Directions directions(String u) { throw new UnsupportedOperationException(); }
    }

    static class FullStubLocationPlatform implements LocationPlatform {
        private static final Place TEST_PLACE = new Place("p-1", "Test Coffee Shop",
                "1 Test Road, London", new Coordinates(51.5, -0.1),
                List.of("cafe", "food"), 4.5, 200, "+44123456", "https://test.coffee",
                PriceLevel.MODERATE);

        private static final PlaceDetail TEST_DETAIL = new PlaceDetail("p-1", "Test Coffee Shop",
                "1 Test Road, London", new Coordinates(51.5, -0.1),
                List.of("cafe", "food"), 4.5, 200, "+44123456", "+44123456",
                "https://test.coffee", PriceLevel.MODERATE, null, List.of(), List.of(),
                "https://maps.example.com/place/p-1");

        @Override public String id() { return "full-location"; }
        @Override public boolean supports(Class<?> c) { return true; }

        @Override public PlaceSearch placeSearch(String u) {
            return new PlaceSearch() {
                @Override public Page<Place> searchByText(String query, PageRequest p) {
                    return new Page<>(List.of(TEST_PLACE), null, false);
                }
                @Override public Page<Place> searchNearby(Coordinates loc, int radius, PageRequest p) {
                    return new Page<>(List.of(TEST_PLACE), null, false);
                }
                @Override public Page<Place> searchByCategory(String cat, Coordinates loc, int radius, PageRequest p) {
                    return new Page<>(List.of(TEST_PLACE), null, false);
                }
            };
        }

        @Override public PlaceDetails placeDetails(String u) {
            return placeId -> TEST_DETAIL;
        }

        @Override public Geocoding geocoding(String u) {
            return new Geocoding() {
                @Override public List<GeocodingResult> geocode(String address) {
                    return List.of(new GeocodingResult("1 Test Road, London",
                            new Coordinates(51.5, -0.1), "p-1", List.of("street_address")));
                }
                @Override public List<GeocodingResult> reverseGeocode(Coordinates loc) {
                    return List.of(new GeocodingResult("1 Test Road, London",
                            loc, "p-1", List.of("street_address")));
                }
            };
        }

        @Override public Directions directions(String u) {
            return (origin, destination, mode) -> {
                var dist = new Distance(11000, "11.0 km");
                var dur = new Duration(1100, "18 min");
                var leg = new RouteLeg("Origin", "Destination", origin, destination, dist, dur);
                return new Route("driving route", dist, dur, List.of(leg));
            };
        }
    }
}
