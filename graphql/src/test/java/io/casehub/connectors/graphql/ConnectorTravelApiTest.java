package io.casehub.connectors.graphql;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.travel.model.Accommodation;
import io.casehub.connectors.travel.model.AccommodationBookingConfirmation;
import io.casehub.connectors.travel.model.AccommodationBookingRequest;
import io.casehub.connectors.travel.model.AccommodationDetail;
import io.casehub.connectors.travel.model.AccommodationType;
import io.casehub.connectors.travel.model.BookingStatus;
import io.casehub.connectors.travel.model.Money;
import io.casehub.connectors.travel.model.TransportBookingConfirmation;
import io.casehub.connectors.travel.model.TransportBookingRequest;
import io.casehub.connectors.travel.model.TransportMode;
import io.casehub.connectors.travel.model.TransportOption;
import io.casehub.connectors.travel.spi.TravelPlatform;
import io.casehub.connectors.travel.spi.TravelPlatformService;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectorTravelApiTest {

    @Test
    void searchTransportThrowsWhenTransportSearchUnsupported() {
        var platform = new StubTravelPlatform("limited", false, false, false, false);
        var service = new TravelPlatformService(List.of(platform));
        var api = apiWith(service);

        assertThatThrownBy(() -> api.searchTransport("limited", "London", "Edinburgh",
                LocalDate.of(2026, 12, 1), null, null, null))
                .isInstanceOf(UnsupportedCapabilityException.class)
                .satisfies(ex -> {
                    var uce = (UnsupportedCapabilityException) ex;
                    assertThat(uce.operation()).isEqualTo("searchTransport");
                    assertThat(uce.capability()).isEqualTo("TransportSearch");
                    assertThat(uce.provider()).isEqualTo("limited");
                    assertThat(uce.supportedCapabilities()).isEmpty();
                });
    }

    @Test
    void bookTransportThrowsWhenTransportBookingUnsupported() {
        var platform = new StubTravelPlatform("search-only", true, false, false, false);
        var service = new TravelPlatformService(List.of(platform));
        var api = apiWith(service);

        assertThatThrownBy(() -> api.bookTransport("search-only",
                new TransportBookingRequest("opt-1", "Jane Doe", "jane@example.com")))
                .isInstanceOf(UnsupportedCapabilityException.class)
                .satisfies(ex -> {
                    var uce = (UnsupportedCapabilityException) ex;
                    assertThat(uce.operation()).isEqualTo("bookTransport");
                    assertThat(uce.capability()).isEqualTo("TransportBooking");
                    assertThat(uce.supportedCapabilities()).containsExactly("TransportSearch");
                });
    }

    @Test
    void searchTransportDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.searchTransport("full-travel", "London", "Edinburgh",
                LocalDate.of(2026, 12, 1), null, null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().origin()).isEqualTo("London");
    }

    @Test
    void searchTransportByModeDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.searchTransport("full-travel", "London", "Edinburgh",
                LocalDate.of(2026, 12, 1), "TRAIN", null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().mode()).isEqualTo(TransportMode.TRAIN);
    }

    @Test
    void searchAccommodationDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.searchAccommodation("full-travel", "Edinburgh",
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), 2, null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().name()).isEqualTo("The Balmoral");
    }

    @Test
    void getAccommodationDetailDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.getAccommodationDetail("full-travel", "acc-1");

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("The Balmoral");
    }

    @Test
    void listTransportBookingsDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.listTransportBookings("full-travel", null, null);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void listAccommodationBookingsDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.listAccommodationBookings("full-travel", null, null);

        assertThat(result.items()).isEmpty();
    }

    private ConnectorTravelApi apiWith(TravelPlatformService service) {
        var identity = mock(SecurityIdentity.class);
        var principal = mock(Principal.class);
        when(identity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("test-user");

        var api = new ConnectorTravelApi();
        api.travelService = service;
        api.identity = identity;
        return api;
    }

    private TravelPlatformService fullService() {
        return new TravelPlatformService(List.of(new FullStubTravelPlatform()));
    }

    record StubTravelPlatform(String id, boolean tSearch, boolean tBooking,
                               boolean aSearch, boolean aBooking) implements TravelPlatform {
        @Override public boolean supports(Class<?> c) {
            if (c == TransportSearch.class) return tSearch;
            if (c == TransportBooking.class) return tBooking;
            if (c == AccommodationSearch.class) return aSearch;
            if (c == AccommodationBooking.class) return aBooking;
            return false;
        }
        @Override public TransportSearch transportSearch() { throw new UnsupportedOperationException(); }
        @Override public TransportBooking transportBooking(String u) { throw new UnsupportedOperationException(); }
        @Override public AccommodationSearch accommodationSearch() { throw new UnsupportedOperationException(); }
        @Override public AccommodationBooking accommodationBooking(String u) { throw new UnsupportedOperationException(); }
    }

    static class FullStubTravelPlatform implements TravelPlatform {
        @Override public String id() { return "full-travel"; }
        @Override public boolean supports(Class<?> c) { return true; }

        @Override public TransportSearch transportSearch() {
            return new TransportSearch() {
                @Override public Page<TransportOption> search(String origin, String destination,
                        LocalDate date, PageRequest p) {
                    return new Page<>(List.of(new TransportOption("t1", origin, destination,
                        TransportMode.TRAIN, "LNER",
                        LocalDateTime.of(2026, 12, 1, 8, 0), LocalDateTime.of(2026, 12, 1, 12, 30),
                        270, new Money(new BigDecimal("89.00"), "GBP"), 42)), null, false);
                }
                @Override public Page<TransportOption> searchByMode(String origin, String destination,
                        LocalDate date, TransportMode mode, PageRequest p) {
                    return new Page<>(List.of(new TransportOption("t1", origin, destination,
                        mode, "LNER",
                        LocalDateTime.of(2026, 12, 1, 8, 0), LocalDateTime.of(2026, 12, 1, 12, 30),
                        270, new Money(new BigDecimal("89.00"), "GBP"), 42)), null, false);
                }
            };
        }

        @Override public TransportBooking transportBooking(String u) {
            return new TransportBooking() {
                @Override public TransportBookingConfirmation book(TransportBookingRequest req) {
                    return new TransportBookingConfirmation("BK-001", req.optionId(), req.passengerName(),
                        BookingStatus.CONFIRMED,
                        LocalDateTime.of(2026, 12, 1, 8, 0), LocalDateTime.of(2026, 12, 1, 12, 30),
                        new Money(new BigDecimal("89.00"), "GBP"));
                }
                @Override public TransportBookingConfirmation getBooking(String ref) { return null; }
                @Override public Page<TransportBookingConfirmation> listBookings(PageRequest p) {
                    return new Page<>(List.of(), null, false);
                }
                @Override public TransportBookingConfirmation cancel(String ref) { return null; }
            };
        }

        @Override public AccommodationSearch accommodationSearch() {
            return new AccommodationSearch() {
                @Override public Page<Accommodation> search(String location, LocalDate checkIn,
                        LocalDate checkOut, int guests, PageRequest p) {
                    return new Page<>(List.of(new Accommodation("acc-1", "The Balmoral",
                        AccommodationType.HOTEL, location, 4.8,
                        new Money(new BigDecimal("250.00"), "GBP"))), null, false);
                }
                @Override public AccommodationDetail getDetail(String accommodationId) {
                    return new AccommodationDetail(accommodationId, "The Balmoral",
                        AccommodationType.HOTEL, "Edinburgh", "Luxury city-centre hotel",
                        4.8, new Money(new BigDecimal("250.00"), "GBP"),
                        List.of("WiFi", "Spa", "Restaurant"), List.of(), List.of());
                }
            };
        }

        @Override public AccommodationBooking accommodationBooking(String u) {
            return new AccommodationBooking() {
                @Override public AccommodationBookingConfirmation book(AccommodationBookingRequest req) {
                    return new AccommodationBookingConfirmation("ABK-001", req.accommodationId(),
                        req.roomType(), req.checkIn(), req.checkOut(), req.guestName(),
                        BookingStatus.CONFIRMED, new Money(new BigDecimal("500.00"), "GBP"));
                }
                @Override public AccommodationBookingConfirmation getBooking(String ref) { return null; }
                @Override public Page<AccommodationBookingConfirmation> listBookings(PageRequest p) {
                    return new Page<>(List.of(), null, false);
                }
                @Override public AccommodationBookingConfirmation cancel(String ref) { return null; }
            };
        }
    }
}
