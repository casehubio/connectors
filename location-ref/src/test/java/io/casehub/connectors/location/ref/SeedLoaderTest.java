package io.casehub.connectors.location.ref;

import io.casehub.connectors.location.model.PriceLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedLoaderTest {

    @Test
    void loadsEightPlaces() {
        var places = SeedLoader.loadPlaces();
        assertThat(places).hasSize(8);
    }

    @Test
    void firstPlaceHasCorrectFields() {
        var place = SeedLoader.loadPlaces().getFirst();
        assertThat(place.name()).isEqualTo("The Italian Kitchen");
        assertThat(place.lat()).isEqualTo(51.5318);
        assertThat(place.priceLevel()).isEqualTo(PriceLevel.MODERATE);
        assertThat(place.reviews()).hasSize(2);
        assertThat(place.openingHours().weekdayText()).hasSize(7);
    }

    @Test
    void loadsFourGeocodingEntries() {
        var entries = SeedLoader.loadGeocoding();
        assertThat(entries).hasSize(4);
        assertThat(entries.getFirst().address()).isEqualTo("King's Cross, London");
    }
}
