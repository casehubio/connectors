package io.casehub.connectors.location.google;

import com.google.maps.model.AddressType;
import com.google.maps.model.DirectionsLeg;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.Geometry;
import com.google.maps.model.LatLng;
import com.google.maps.model.PlacesSearchResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleLocationPlatformTest {

    @Test
    void mapsSearchResultToPlace() {
        var result = new PlacesSearchResult();
        result.placeId = "ChIJ123";
        result.name = "The Italian Kitchen";
        result.formattedAddress = "10 King's Cross Rd, London";
        result.geometry = new Geometry();
        result.geometry.location = new LatLng(51.5318, -0.1239);
        result.types = new String[]{"restaurant", "food"};
        result.rating = 4.5f;
        result.userRatingsTotal = 320;

        var place = GoogleLocationPlatform.mapPlace(result);

        assertThat(place.id()).isEqualTo("ChIJ123");
        assertThat(place.name()).isEqualTo("The Italian Kitchen");
        assertThat(place.formattedAddress()).isEqualTo("10 King's Cross Rd, London");
        assertThat(place.location().lat()).isEqualTo(51.5318);
        assertThat(place.location().lng()).isEqualTo(-0.1239);
        assertThat(place.types()).containsExactly("restaurant", "food");
        assertThat(place.rating()).isEqualTo(4.5);
        assertThat(place.userRatingsTotal()).isEqualTo(320);
    }

    @Test
    void mapsSearchResultWithMinimalFields() {
        var result = new PlacesSearchResult();
        result.placeId = "ChIJ456";
        result.name = "Some Place";

        var place = GoogleLocationPlatform.mapPlace(result);

        assertThat(place.id()).isEqualTo("ChIJ456");
        assertThat(place.name()).isEqualTo("Some Place");
        assertThat(place.location()).isNull();
        assertThat(place.types()).isEmpty();
        assertThat(place.rating()).isNull();
        assertThat(place.priceLevel()).isNull();
    }

    @Test
    void mapsSearchResultFallsBackToVicinity() {
        var result = new PlacesSearchResult();
        result.placeId = "ChIJ789";
        result.name = "A Café";
        result.vicinity = "Near the station";

        var place = GoogleLocationPlatform.mapPlace(result);
        assertThat(place.formattedAddress()).isEqualTo("Near the station");
    }

    @Test
    void mapsPlaceDetail() {
        var detail = new com.google.maps.model.PlaceDetails();
        detail.name = "The Italian Kitchen";
        detail.formattedAddress = "10 King's Cross Rd, London";
        detail.geometry = new Geometry();
        detail.geometry.location = new LatLng(51.5318, -0.1239);
        detail.types = new AddressType[]{AddressType.RESTAURANT, AddressType.FOOD};
        detail.rating = 4.5f;
        detail.userRatingsTotal = 320;
        detail.internationalPhoneNumber = "+442071234001";
        detail.formattedPhoneNumber = "020 7123 4001";
        detail.priceLevel = com.google.maps.model.PriceLevel.MODERATE;
        detail.openingHours = new com.google.maps.model.OpeningHours();
        detail.openingHours.weekdayText = new String[]{"Mon: 11:00-22:00"};
        detail.openingHours.openNow = true;
        var review = new com.google.maps.model.PlaceDetails.Review();
        review.authorName = "Alice S.";
        review.rating = 5;
        review.text = "Best pasta in the area";
        review.time = Instant.ofEpochMilli(1727900000000L);
        detail.reviews = new com.google.maps.model.PlaceDetails.Review[]{review};

        var mapped = GoogleLocationPlatform.mapPlaceDetail("ChIJ123", detail);

        assertThat(mapped.id()).isEqualTo("ChIJ123");
        assertThat(mapped.name()).isEqualTo("The Italian Kitchen");
        assertThat(mapped.phoneNumber()).isEqualTo("+442071234001");
        assertThat(mapped.formattedPhoneNumber()).isEqualTo("020 7123 4001");
        assertThat(mapped.priceLevel()).isEqualTo(
            io.casehub.connectors.location.model.PriceLevel.MODERATE);
        assertThat(mapped.openingHours().weekdayText()).containsExactly("Mon: 11:00-22:00");
        assertThat(mapped.openingHours().openNow()).isTrue();
        assertThat(mapped.reviews()).hasSize(1);
        assertThat(mapped.reviews().getFirst().author()).isEqualTo("Alice S.");
        assertThat(mapped.reviews().getFirst().rating()).isEqualTo(5.0);
    }

    @Test
    void mapsGeocodingResult() {
        var result = new com.google.maps.model.GeocodingResult();
        result.formattedAddress = "King's Cross, London, UK";
        result.geometry = new Geometry();
        result.geometry.location = new LatLng(51.5318, -0.1239);
        result.placeId = "ChIJkx";
        result.types = new AddressType[]{AddressType.NEIGHBORHOOD};

        var mapped = GoogleLocationPlatform.mapGeocodingResult(result);

        assertThat(mapped.formattedAddress()).isEqualTo("King's Cross, London, UK");
        assertThat(mapped.location().lat()).isEqualTo(51.5318);
        assertThat(mapped.placeId()).isEqualTo("ChIJkx");
        assertThat(mapped.types()).containsExactly("NEIGHBORHOOD");
    }

    @Test
    void mapsDirectionsResult() {
        var leg = new DirectionsLeg();
        leg.startAddress = "King's Cross";
        leg.endAddress = "London Bridge";
        leg.startLocation = new LatLng(51.5318, -0.1239);
        leg.endLocation = new LatLng(51.5045, -0.0865);
        leg.distance = new com.google.maps.model.Distance();
        leg.distance.inMeters = 4200;
        leg.distance.humanReadable = "4.2 km";
        leg.duration = new com.google.maps.model.Duration();
        leg.duration.inSeconds = 900;
        leg.duration.humanReadable = "15 mins";

        var route = new DirectionsRoute();
        route.summary = "A501";
        route.legs = new DirectionsLeg[]{leg};

        var result = new DirectionsResult();
        result.routes = new DirectionsRoute[]{route};

        var mapped = GoogleLocationPlatform.mapRoute(result);

        assertThat(mapped.summary()).isEqualTo("A501");
        assertThat(mapped.distance().meters()).isEqualTo(4200);
        assertThat(mapped.duration().seconds()).isEqualTo(900);
        assertThat(mapped.legs()).hasSize(1);
        assertThat(mapped.legs().getFirst().startAddress()).isEqualTo("King's Cross");
    }

    @Test
    void mapsEmptyDirectionsResult() {
        var result = new DirectionsResult();
        result.routes = new DirectionsRoute[0];

        var mapped = GoogleLocationPlatform.mapRoute(result);

        assertThat(mapped.summary()).isEqualTo("No route found");
        assertThat(mapped.legs()).isEmpty();
    }

    @Test
    void mapsNullPlacesArrayToEmptyList() {
        var places = GoogleLocationPlatform.mapPlaces(null);
        assertThat(places).isEmpty();
    }
}
