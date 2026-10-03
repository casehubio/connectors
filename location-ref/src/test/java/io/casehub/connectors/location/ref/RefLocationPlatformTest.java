package io.casehub.connectors.location.ref;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.location.model.Coordinates;
import io.casehub.connectors.location.model.TravelMode;
import io.casehub.connectors.location.spi.LocationPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefLocationPlatformTest {

    private RefLocationPlatform platform;

    @BeforeEach
    void setUp() {
        platform = new RefLocationPlatform(new InMemoryLocationBackend());
    }

    @Test
    void id() {
        assertThat(platform.id()).isEqualTo("ref");
    }

    @Test
    void supportsAllCapabilities() {
        assertThat(platform.supports(LocationPlatform.PlaceSearch.class)).isTrue();
        assertThat(platform.supports(LocationPlatform.PlaceDetails.class)).isTrue();
        assertThat(platform.supports(LocationPlatform.Geocoding.class)).isTrue();
        assertThat(platform.supports(LocationPlatform.Directions.class)).isTrue();
    }

    @Test
    void searchByTextFindsRestaurants() {
        var results = platform.placeSearch("user1")
            .searchByText("Italian", new PageRequest(null, 10));
        assertThat(results.items()).isNotEmpty();
        assertThat(results.items()).allSatisfy(p ->
            assertThat(p.name().toLowerCase() + " " + String.join(" ", p.types()))
                .containsIgnoringCase("italian"));
    }

    @Test
    void searchByTextPaginates() {
        var page1 = platform.placeSearch("user1")
            .searchByText("London", new PageRequest(null, 3));
        assertThat(page1.items()).hasSize(3);
        assertThat(page1.hasMore()).isTrue();

        var page2 = platform.placeSearch("user1")
            .searchByText("London", new PageRequest(page1.nextCursor(), 3));
        assertThat(page2.items()).isNotEmpty();
    }

    @Test
    void searchNearbyFindsPlacesWithinRadius() {
        var kingsCross = new Coordinates(51.5318, -0.1239);
        var results = platform.placeSearch("user1")
            .searchNearby(kingsCross, 500, new PageRequest(null, 20));
        assertThat(results.items()).isNotEmpty();
        assertThat(results.items().size()).isLessThan(8);
    }

    @Test
    void searchByCategoryFiltersResults() {
        var kingsCross = new Coordinates(51.5318, -0.1239);
        var results = platform.placeSearch("user1")
            .searchByCategory("restaurant", kingsCross, 5000, new PageRequest(null, 20));
        assertThat(results.items()).isNotEmpty();
        assertThat(results.items()).allSatisfy(p ->
            assertThat(p.types()).anyMatch(t -> t.contains("restaurant")));
    }

    @Test
    void getPlaceDetailReturnsFullInfo() {
        var places = platform.placeSearch("user1")
            .searchByText("Italian Kitchen", new PageRequest(null, 1));
        var placeId = places.items().getFirst().id();

        var detail = platform.placeDetails("user1").get(placeId);
        assertThat(detail.id()).isEqualTo(placeId);
        assertThat(detail.name()).isEqualTo("The Italian Kitchen");
        assertThat(detail.openingHours()).isNotNull();
        assertThat(detail.openingHours().weekdayText()).isNotEmpty();
        assertThat(detail.reviews()).isNotEmpty();
        assertThat(detail.phoneNumber()).isNotNull();
        assertThat(detail.url()).isNotNull();
    }

    @Test
    void getPlaceDetailThrowsForUnknown() {
        assertThatThrownBy(() -> platform.placeDetails("user1").get("unknown"))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void geocodeResolvesAddress() {
        var results = platform.geocoding("user1").geocode("King's Cross");
        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().formattedAddress()).containsIgnoringCase("King's Cross");
        assertThat(results.getFirst().location()).isNotNull();
    }

    @Test
    void reverseGeocodeFindsNearbyAddresses() {
        var results = platform.geocoding("user1")
            .reverseGeocode(new Coordinates(51.5318, -0.1239));
        assertThat(results).isNotEmpty();
        assertThat(results).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void routeCalculatesDistanceAndDuration() {
        var origin = new Coordinates(51.5318, -0.1239);
        var destination = new Coordinates(51.5045, -0.0865);
        var route = platform.directions("user1")
            .route(origin, destination, TravelMode.DRIVING);

        assertThat(route.distance().meters()).isGreaterThan(0);
        assertThat(route.duration().seconds()).isGreaterThan(0);
        assertThat(route.legs()).hasSize(1);
        assertThat(route.summary()).isEqualTo("driving route");
    }

    @Test
    void routeWalkingSlowerThanDriving() {
        var origin = new Coordinates(51.5318, -0.1239);
        var destination = new Coordinates(51.5045, -0.0865);
        var driving = platform.directions("user1")
            .route(origin, destination, TravelMode.DRIVING);
        var walking = platform.directions("user1")
            .route(origin, destination, TravelMode.WALKING);

        assertThat(walking.duration().seconds())
            .isGreaterThan(driving.duration().seconds());
    }
}
