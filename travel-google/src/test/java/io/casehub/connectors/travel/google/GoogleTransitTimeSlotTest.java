package io.casehub.connectors.travel.google;

import io.casehub.connectors.travel.model.Money;
import io.casehub.connectors.travel.model.TransportMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleTransitTimeSlotTest {

    private static final LocalDate SEARCH_DATE = LocalDate.of(2026, 12, 20);
    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    @Test
    void defaultSlots_issues6Requests() {
        var callCount = new AtomicInteger();
        var client = stubClient((origin, dest, time) -> {
            callCount.incrementAndGet();
            return List.of();
        });
        var platform = new GoogleTransitTravelPlatform(client, null);

        platform.transportSearch().search("London", "Oxford", SEARCH_DATE, null);

        assertThat(callCount.get()).isEqualTo(6);
    }

    @Test
    void customSlots_respectsConfiguration() {
        var departures = new ArrayList<Instant>();
        var client = stubClient((origin, dest, time) -> {
            departures.add(time);
            return List.of();
        });
        var customSlots = List.of(LocalTime.of(7, 0), LocalTime.of(19, 0));
        var platform = new GoogleTransitTravelPlatform(client, customSlots);

        platform.transportSearch().search("London", "Oxford", SEARCH_DATE, null);

        assertThat(departures).hasSize(2);
        assertThat(departures.get(0))
                .isEqualTo(SEARCH_DATE.atTime(7, 0).atZone(LONDON).toInstant());
        assertThat(departures.get(1))
                .isEqualTo(SEARCH_DATE.atTime(19, 0).atZone(LONDON).toInstant());
    }

    @Test
    void partialFailure_returnsSuccessfulResults() {
        var callCount = new AtomicInteger();
        var client = stubClient((origin, dest, time) -> {
            int n = callCount.incrementAndGet();
            if (n % 2 == 0) throw new RuntimeException("API error");
            return List.of(route("route-" + n, 8 + n * 2));
        });
        var platform = new GoogleTransitTravelPlatform(client, null);

        var result = platform.transportSearch().search("London", "Oxford", SEARCH_DATE, null);

        assertThat(callCount.get()).isEqualTo(6);
        assertThat(result.items()).hasSize(3);
    }

    @Test
    void allEmpty_returnsEmptyPage() {
        var client = stubClient((origin, dest, time) -> List.of());
        var platform = new GoogleTransitTravelPlatform(client, null);

        var result = platform.transportSearch().search("London", "Oxford", SEARCH_DATE, null);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void multiSlotResults_areMergedAndDeduplicated() {
        var client = stubClient((origin, dest, time) -> {
            var hour = time.atZone(LONDON).getHour();
            if (hour == 6 || hour == 9) {
                return List.of(
                        route("early", 10),
                        route("mid", 12));
            }
            return List.of(route("late", 16));
        });
        var platform = new GoogleTransitTravelPlatform(client, null);

        var result = platform.transportSearch().search("London", "Oxford", SEARCH_DATE, null);

        assertThat(result.items()).hasSize(3);
    }

    private static TransitRoute route(String id, int departureHour) {
        return new TransitRoute(id, "London", "Oxford",
                TransportMode.TRAIN, "GWR",
                LocalDateTime.of(2026, 12, 20, departureHour, 30),
                LocalDateTime.of(2026, 12, 20, departureHour + 1, 30),
                60, new Money(new BigDecimal("25.00"), "GBP"));
    }

    @FunctionalInterface
    interface TransitSearchHandler {
        List<TransitRoute> search(String origin, String destination, Instant departureTime);
    }

    private static GoogleTransitClient stubClient(TransitSearchHandler handler) {
        return new GoogleTransitClient("dummy-key") {
            @Override
            public List<TransitRoute> searchTransit(String origin, String destination,
                                                     Instant departureTime) {
                return handler.search(origin, destination, departureTime);
            }
        };
    }
}
