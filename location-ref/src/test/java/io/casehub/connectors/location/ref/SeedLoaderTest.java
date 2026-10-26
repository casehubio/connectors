package io.casehub.connectors.location.ref;

import io.casehub.connectors.location.model.PriceLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedLoaderTest {

    @Test
    void loadsLifeDomainPlaces() {
        var places = SeedLoader.loadPlaces();
        assertThat(places).hasSize(12);
        assertThat(places).extracting("name")
                          .contains("Elm Street Surgery", "PureGym Clapham", "Oakwood Academy");
    }

    @Test
    void firstPlaceHasCorrectFields() {
        var place = SeedLoader.loadPlaces().getFirst();
        assertThat(place.name()).isEqualTo("Elm Street Surgery");
        assertThat(place.lat()).isEqualTo(51.4485);
        assertThat(place.priceLevel()).isEqualTo(PriceLevel.FREE);
        assertThat(place.reviews()).hasSize(1);
        assertThat(place.openingHours().weekdayText()).hasSize(7);
    }

    @Test
    void loadsEightGeocodingEntries() {
        var entries = SeedLoader.loadGeocoding();
        assertThat(entries).hasSize(8);
        assertThat(entries.getFirst().address()).contains("Ramsden Road");
    }
}
