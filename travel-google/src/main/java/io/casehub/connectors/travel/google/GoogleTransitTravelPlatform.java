package io.casehub.connectors.travel.google;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.travel.model.Money;
import io.casehub.connectors.travel.model.TransportMode;
import io.casehub.connectors.travel.model.TransportOption;
import io.casehub.connectors.travel.spi.TravelPlatform;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class GoogleTransitTravelPlatform implements TravelPlatform {

    private static final Logger LOG = Logger.getLogger(GoogleTransitTravelPlatform.class.getName());
    private static final Set<Class<?>> SUPPORTED = Set.of(TransportSearch.class);
    private static final List<LocalTime> DEFAULT_SLOTS = List.of(
            LocalTime.of(6, 0), LocalTime.of(9, 0), LocalTime.of(12, 0),
            LocalTime.of(15, 0), LocalTime.of(18, 0), LocalTime.of(21, 0));

    private final GoogleTransitClient client;
    private final List<LocalTime> departureSlots;

    public GoogleTransitTravelPlatform(GoogleTransitClient client, List<LocalTime> departureSlots) {
        this.client = client;
        this.departureSlots = departureSlots != null && !departureSlots.isEmpty()
                ? departureSlots : DEFAULT_SLOTS;
    }

    @Override
    public String id() {
        return "google-transit";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return SUPPORTED.contains(capability);
    }

    @Override
    public TransportSearch transportSearch() {
        return new GoogleTransportSearch();
    }

    @Override
    public TransportBooking transportBooking(String userId) {
        throw new UnsupportedCapabilityException("transportBooking",
                "TransportBooking", id(), List.of("TransportSearch"));
    }

    @Override
    public AccommodationSearch accommodationSearch() {
        throw new UnsupportedCapabilityException("accommodationSearch",
                "AccommodationSearch", id(), List.of("TransportSearch"));
    }

    @Override
    public AccommodationBooking accommodationBooking(String userId) {
        throw new UnsupportedCapabilityException("accommodationBooking",
                "AccommodationBooking", id(), List.of("TransportSearch"));
    }

    static List<TransitRoute> deduplicate(List<TransitRoute> routes) {
        var seen = new LinkedHashSet<String>();
        var result = new ArrayList<TransitRoute>();
        for (var route : routes) {
            var key = route.departure().withSecond(0).withNano(0)
                    + "|" + route.operator() + "|" + route.mode();
            if (seen.add(key)) {
                result.add(route);
            }
        }
        return result;
    }

    private class GoogleTransportSearch implements TransportSearch {
        @Override
        public Page<TransportOption> search(String origin, String destination,
                                             LocalDate date, PageRequest pagination) {
            var allRoutes = new ArrayList<TransitRoute>();
            var zone = ZoneId.of("Europe/London");
            for (var slot : departureSlots) {
                try {
                    var departureTime = date.atTime(slot).atZone(zone).toInstant();
                    var routes = client.searchTransit(origin, destination, departureTime);
                    allRoutes.addAll(routes);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Transit search failed for slot " + slot, e);
                }
            }
            var deduped = deduplicate(allRoutes);
            var options = deduped.stream()
                    .map(GoogleTransitTravelPlatform::toTransportOption)
                    .collect(Collectors.toList());
            return Page.of(options);
        }

        @Override
        public Page<TransportOption> searchByMode(String origin, String destination,
                                                    LocalDate date, TransportMode mode,
                                                    PageRequest pagination) {
            var all = search(origin, destination, date, pagination);
            var filtered = all.items().stream()
                    .filter(o -> o.mode() == mode)
                    .collect(Collectors.toList());
            return Page.of(filtered);
        }
    }

    private static TransportOption toTransportOption(TransitRoute route) {
        return new TransportOption(
                route.id(),
                route.origin(),
                route.destination(),
                route.mode(),
                route.operator(),
                route.departure(),
                route.arrival(),
                route.durationMinutes(),
                route.fare() != null ? route.fare() : new Money(BigDecimal.ZERO, "GBP"),
                0);
    }
}
