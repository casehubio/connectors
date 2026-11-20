package io.casehub.connectors.location.ref;

import io.casehub.connectors.location.model.BatteryStatus;
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

    @Test
    void loadsThreeTrackedDevices() {
        var devices = SeedLoader.loadTrackedDevices();
        assertThat(devices).hasSize(3);
        assertThat(devices).extracting("name")
                           .containsExactlyInAnyOrder("Keys", "Backpack", "Dog Collar");
    }

    @Test
    void trackedDeviceFieldsDeserialiseCorrectly() {
        var keys = SeedLoader.loadTrackedDevices().stream()
                             .filter(d -> d.name().equals("Keys")).findFirst().orElseThrow();
        assertThat(keys.id()).isEqualTo("tracker-1");
        assertThat(keys.type()).isEqualTo("airtag");
        assertThat(keys.lat()).isEqualTo(51.4435);
        assertThat(keys.lng()).isEqualTo(-0.1530);
        assertThat(keys.accuracyMeters()).isEqualTo(5.0);
        assertThat(keys.batteryStatus()).isEqualTo(BatteryStatus.GOOD);
        assertThat(keys.levelPercent()).isEqualTo(72);
    }

    @Test
    void nullableLevelPercentHandledCorrectly() {
        var devices  = SeedLoader.loadTrackedDevices();
        var backpack = devices.stream().filter(d -> d.name().equals("Backpack")).findFirst().orElseThrow();
        assertThat(backpack.batteryStatus()).isEqualTo(BatteryStatus.LOW);
        assertThat(backpack.levelPercent()).isNull();

        var collar = devices.stream().filter(d -> d.name().equals("Dog Collar")).findFirst().orElseThrow();
        assertThat(collar.batteryStatus()).isEqualTo(BatteryStatus.FULL);
        assertThat(collar.levelPercent()).isNull();
    }

}
