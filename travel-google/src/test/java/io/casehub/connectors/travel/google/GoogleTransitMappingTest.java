package io.casehub.connectors.travel.google;

import com.google.maps.model.VehicleType;
import io.casehub.connectors.travel.model.TransportMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleTransitMappingTest {

    @ParameterizedTest
    @CsvSource({
            "RAIL, TRAIN",
            "HEAVY_RAIL, TRAIN",
            "COMMUTER_TRAIN, TRAIN",
            "HIGH_SPEED_TRAIN, TRAIN",
            "LONG_DISTANCE_TRAIN, TRAIN",
            "SUBWAY, TRAIN",
            "METRO_RAIL, TRAIN",
            "TRAM, TRAIN",
            "MONORAIL, TRAIN",
            "BUS, BUS",
            "INTERCITY_BUS, BUS",
            "TROLLEYBUS, BUS",
            "FERRY, FERRY"
    })
    void mapVehicleType_mapsToCorrectTransportMode(String vehicleType, String expected) {
        var result = GoogleTransitClient.mapVehicleType(VehicleType.valueOf(vehicleType));
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(TransportMode.valueOf(expected));
    }

    @Test
    void mapVehicleType_unknownTypeReturnsEmpty() {
        var result = GoogleTransitClient.mapVehicleType(VehicleType.OTHER);
        assertThat(result).isEmpty();
    }

    @Test
    void mapVehicleType_nullReturnsEmpty() {
        var result = GoogleTransitClient.mapVehicleType(null);
        assertThat(result).isEmpty();
    }

    @Test
    void syntheticId_isConsistentForSameInputs() {
        var id1 = GoogleTransitClient.syntheticId(
                LocalDateTime.of(2026, 12, 20, 8, 0),
                LocalDateTime.of(2026, 12, 20, 10, 15),
                "National Rail", TransportMode.TRAIN);
        var id2 = GoogleTransitClient.syntheticId(
                LocalDateTime.of(2026, 12, 20, 8, 0),
                LocalDateTime.of(2026, 12, 20, 10, 15),
                "National Rail", TransportMode.TRAIN);
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void syntheticId_differsByDepartureTime() {
        var id1 = GoogleTransitClient.syntheticId(
                LocalDateTime.of(2026, 12, 20, 8, 0),
                LocalDateTime.of(2026, 12, 20, 10, 15),
                "National Rail", TransportMode.TRAIN);
        var id2 = GoogleTransitClient.syntheticId(
                LocalDateTime.of(2026, 12, 20, 9, 0),
                LocalDateTime.of(2026, 12, 20, 11, 15),
                "National Rail", TransportMode.TRAIN);
        assertThat(id1).isNotEqualTo(id2);
    }
}
