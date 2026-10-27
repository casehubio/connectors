package io.casehub.connectors.travel.amadeus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.travel.model.Money;
import io.casehub.connectors.travel.model.TransportMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AmadeusFlightSearchTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static JsonNode flightResponse;

    @BeforeAll
    static void loadFixture() throws IOException {
        flightResponse = MAPPER.readTree(
                AmadeusFlightSearchTest.class.getResourceAsStream(
                        "/amadeus-flight-offers-response.json"));
    }

    @Test
    void mapFlightOffers_mapsAllFields() {
        var offers = AmadeusClient.mapFlightOffers(flightResponse);
        assertThat(offers).hasSize(2);

        var first = offers.get(0);
        assertThat(first.id()).isEqualTo("1");
        assertThat(first.origin()).isEqualTo("LHR");
        assertThat(first.destination()).isEqualTo("CDG");
        assertThat(first.mode()).isEqualTo(TransportMode.FLIGHT);
        assertThat(first.operator()).isEqualTo("BRITISH AIRWAYS");
        assertThat(first.departure()).isEqualTo(LocalDateTime.of(2026, 12, 20, 8, 0));
        assertThat(first.arrival()).isEqualTo(LocalDateTime.of(2026, 12, 20, 10, 15));
        assertThat(first.durationMinutes()).isEqualTo(135);
        assertThat(first.price()).isEqualTo(new Money(new BigDecimal("125.50"), "GBP"));
        assertThat(first.availableSeats()).isEqualTo(0);
    }

    @Test
    void mapFlightOffers_secondOffer() {
        var offers = AmadeusClient.mapFlightOffers(flightResponse);
        var second = offers.get(1);
        assertThat(second.id()).isEqualTo("2");
        assertThat(second.operator()).isEqualTo("AIR FRANCE");
        assertThat(second.price()).isEqualTo(new Money(new BigDecimal("98.00"), "GBP"));
        assertThat(second.departure()).isEqualTo(LocalDateTime.of(2026, 12, 20, 14, 30));
        assertThat(second.arrival()).isEqualTo(LocalDateTime.of(2026, 12, 20, 16, 55));
        assertThat(second.durationMinutes()).isEqualTo(145);
    }

    @Test
    void searchByMode_nonFlightReturnsEmpty() {
        var platform = new AmadeusTravelPlatform(null);
        var result = platform.transportSearch().searchByMode(
                "LHR", "CDG", LocalDate.now(),
                TransportMode.TRAIN,
                PageRequest.first(10));
        assertThat(result.items()).isEmpty();
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    void supports_transportSearch() {
        var platform = new AmadeusTravelPlatform(null);
        assertThat(platform.supports(
                io.casehub.connectors.travel.spi.TravelPlatform.TransportSearch.class)).isTrue();
        assertThat(platform.supports(
                io.casehub.connectors.travel.spi.TravelPlatform.AccommodationSearch.class)).isTrue();
        assertThat(platform.supports(
                io.casehub.connectors.travel.spi.TravelPlatform.TransportBooking.class)).isFalse();
    }

    @Test
    void id_returnsAmadeus() {
        var platform = new AmadeusTravelPlatform(null);
        assertThat(platform.id()).isEqualTo("amadeus");
    }
}
