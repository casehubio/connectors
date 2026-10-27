package io.casehub.connectors.travel.google;

import io.casehub.connectors.travel.model.Money;
import io.casehub.connectors.travel.model.TransportMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleTransitDeduplicationTest {

    @Test
    void deduplicate_removesDuplicatesByDepartureOperatorMode() {
        var route1 = new TransitRoute("id1", "London", "Oxford",
                TransportMode.TRAIN, "GWR",
                LocalDateTime.of(2026, 12, 20, 10, 30),
                LocalDateTime.of(2026, 12, 20, 11, 30),
                60, new Money(new BigDecimal("25.00"), "GBP"));
        var route2 = new TransitRoute("id2", "London", "Oxford",
                TransportMode.TRAIN, "GWR",
                LocalDateTime.of(2026, 12, 20, 10, 30),
                LocalDateTime.of(2026, 12, 20, 11, 30),
                60, new Money(new BigDecimal("25.00"), "GBP"));
        var route3 = new TransitRoute("id3", "London", "Oxford",
                TransportMode.TRAIN, "GWR",
                LocalDateTime.of(2026, 12, 20, 14, 0),
                LocalDateTime.of(2026, 12, 20, 15, 0),
                60, new Money(new BigDecimal("25.00"), "GBP"));

        var deduped = GoogleTransitTravelPlatform.deduplicate(List.of(route1, route2, route3));
        assertThat(deduped).hasSize(2);
    }

    @Test
    void deduplicate_keepsDifferentModes() {
        var train = new TransitRoute("id1", "London", "Oxford",
                TransportMode.TRAIN, "GWR",
                LocalDateTime.of(2026, 12, 20, 10, 30),
                LocalDateTime.of(2026, 12, 20, 11, 30),
                60, null);
        var bus = new TransitRoute("id2", "London", "Oxford",
                TransportMode.BUS, "National Express",
                LocalDateTime.of(2026, 12, 20, 10, 30),
                LocalDateTime.of(2026, 12, 20, 12, 30),
                120, null);

        var deduped = GoogleTransitTravelPlatform.deduplicate(List.of(train, bus));
        assertThat(deduped).hasSize(2);
    }

    @Test
    void deduplicate_preservesOrder() {
        var early = new TransitRoute("id1", "London", "Oxford",
                TransportMode.TRAIN, "GWR",
                LocalDateTime.of(2026, 12, 20, 8, 0),
                LocalDateTime.of(2026, 12, 20, 9, 0),
                60, null);
        var late = new TransitRoute("id2", "London", "Oxford",
                TransportMode.TRAIN, "GWR",
                LocalDateTime.of(2026, 12, 20, 14, 0),
                LocalDateTime.of(2026, 12, 20, 15, 0),
                60, null);

        var deduped = GoogleTransitTravelPlatform.deduplicate(List.of(early, late));
        assertThat(deduped.get(0).departure().getHour()).isEqualTo(8);
        assertThat(deduped.get(1).departure().getHour()).isEqualTo(14);
    }

    @Test
    void platform_hasCorrectIdAndCapabilities() {
        var platform = new GoogleTransitTravelPlatform(null, List.of());
        assertThat(platform.id()).isEqualTo("google-transit");
        assertThat(platform.supports(
                io.casehub.connectors.travel.spi.TravelPlatform.TransportSearch.class)).isTrue();
        assertThat(platform.supports(
                io.casehub.connectors.travel.spi.TravelPlatform.AccommodationSearch.class)).isFalse();
        assertThat(platform.supports(
                io.casehub.connectors.travel.spi.TravelPlatform.TransportBooking.class)).isFalse();
    }
}
