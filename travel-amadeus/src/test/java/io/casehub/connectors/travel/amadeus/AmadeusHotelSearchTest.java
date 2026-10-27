package io.casehub.connectors.travel.amadeus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.connectors.travel.model.AccommodationType;
import io.casehub.connectors.travel.model.Money;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AmadeusHotelSearchTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static JsonNode hotelOffersResponse;

    @BeforeAll
    static void loadFixtures() throws IOException {
        hotelOffersResponse = MAPPER.readTree(
                AmadeusHotelSearchTest.class.getResourceAsStream(
                        "/amadeus-hotel-offers-response.json"));
    }

    @Test
    void mapHotelOffers_mapsAccommodation() {
        var results = AmadeusClient.mapHotelOffers(hotelOffersResponse, 1);
        assertThat(results).hasSize(1);

        var hotel = results.get(0);
        assertThat(hotel.id()).isEqualTo("HLLONBIG");
        assertThat(hotel.name()).isEqualTo("The Big Hotel London");
        assertThat(hotel.type()).isEqualTo(AccommodationType.HOTEL);
        assertThat(hotel.location()).isEqualTo("London");
        assertThat(hotel.rating()).isEqualTo(4.0);
        assertThat(hotel.pricePerNight()).isEqualTo(new Money(new BigDecimal("150.00"), "GBP"));
    }

    @Test
    void mapHotelOffers_dividesPriceByNights() {
        var results = AmadeusClient.mapHotelOffers(hotelOffersResponse, 2);
        assertThat(results.get(0).pricePerNight()).isEqualTo(
                new Money(new BigDecimal("75.00"), "GBP"));
    }

    @Test
    void mapHotelDetail_mapsAllFields() {
        var detail = AmadeusClient.mapHotelDetail(hotelOffersResponse.path("data").get(0));
        assertThat(detail.id()).isEqualTo("HLLONBIG");
        assertThat(detail.name()).isEqualTo("The Big Hotel London");
        assertThat(detail.description()).isEqualTo("A grand hotel in central London with excellent views.");
        assertThat(detail.amenities()).containsExactlyInAnyOrder(
                "SWIMMING_POOL", "SPA", "FITNESS_CENTER", "RESTAURANT", "WIFI");
        assertThat(detail.images()).hasSize(2);
        assertThat(detail.roomTypes()).hasSize(2);
        assertThat(detail.roomTypes().get(0).name()).isEqualTo("STANDARD_ROOM");
        assertThat(detail.roomTypes().get(0).capacity()).isEqualTo(1);
        assertThat(detail.roomTypes().get(1).name()).isEqualTo("SUITE");
    }
}
