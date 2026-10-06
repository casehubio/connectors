package io.casehub.connectors.travel.ref;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.travel.model.AccommodationBookingRequest;
import io.casehub.connectors.travel.model.BookingStatus;
import io.casehub.connectors.travel.model.TransportBookingRequest;
import io.casehub.connectors.travel.model.TransportMode;
import io.casehub.connectors.travel.spi.TravelPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefTravelPlatformTest {

    private RefTravelPlatform platform;

    @BeforeEach
    void setUp() {
        platform = new RefTravelPlatform(new InMemoryTravelBackend());
    }

    @Test
    void id() {
        assertThat(platform.id()).isEqualTo("ref");
    }

    @Test
    void supportsAllCapabilities() {
        assertThat(platform.supports(TravelPlatform.TransportSearch.class)).isTrue();
        assertThat(platform.supports(TravelPlatform.TransportBooking.class)).isTrue();
        assertThat(platform.supports(TravelPlatform.AccommodationSearch.class)).isTrue();
        assertThat(platform.supports(TravelPlatform.AccommodationBooking.class)).isTrue();
    }

    @Test
    void transportSearchFindsOptions() {
        var results = platform.transportSearch()
            .search("London", "Paris", LocalDate.of(2026, 12, 1), PageRequest.first(10));
        assertThat(results.items()).isNotEmpty();
        assertThat(results.items()).allSatisfy(opt -> {
            assertThat(opt.origin().toLowerCase()).contains("london");
            assertThat(opt.destination().toLowerCase()).contains("paris");
        });
    }

    @Test
    void transportSearchPaginates() {
        var page1 = platform.transportSearch()
            .search("London", "Paris", LocalDate.of(2026, 12, 1), PageRequest.first(1));
        assertThat(page1.items()).hasSize(1);
        assertThat(page1.hasMore()).isTrue();

        var page2 = platform.transportSearch()
            .search("London", "Paris", LocalDate.of(2026, 12, 1),
                new PageRequest(page1.nextCursor(), 1));
        assertThat(page2.items()).isNotEmpty();
    }

    @Test
    void transportSearchByModeFilters() {
        var results = platform.transportSearch()
            .searchByMode("London", "Paris", LocalDate.of(2026, 12, 1),
                TransportMode.TRAIN, PageRequest.first(10));
        assertThat(results.items()).isNotEmpty();
        assertThat(results.items()).allSatisfy(opt ->
            assertThat(opt.mode()).isEqualTo(TransportMode.TRAIN));
    }

    @Test
    void transportBookingLifecycle() {
        var options = platform.transportSearch()
            .search("London", "Paris", LocalDate.of(2026, 12, 1), PageRequest.first(1));
        var option = options.items().getFirst();

        var confirmation = platform.transportBooking("user1")
            .book(new TransportBookingRequest(option.id(), "Jane Doe", "jane@example.com"));
        assertThat(confirmation.bookingReference()).isNotBlank();
        assertThat(confirmation.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmation.optionId()).isEqualTo(option.id());
        assertThat(confirmation.passengerName()).isEqualTo("Jane Doe");
        assertThat(confirmation.price().amount()).isGreaterThan(BigDecimal.ZERO);

        var retrieved = platform.transportBooking("user1")
            .getBooking(confirmation.bookingReference());
        assertThat(retrieved.bookingReference()).isEqualTo(confirmation.bookingReference());

        var cancelled = platform.transportBooking("user1")
            .cancel(confirmation.bookingReference());
        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void transportBookingsAreUserScoped() {
        var options = platform.transportSearch()
            .search("London", "Paris", LocalDate.of(2026, 12, 1), PageRequest.first(1));
        var option = options.items().getFirst();

        platform.transportBooking("user1")
            .book(new TransportBookingRequest(option.id(), "Jane Doe", "jane@example.com"));

        var user1Bookings = platform.transportBooking("user1")
            .listBookings(PageRequest.first(10));
        var user2Bookings = platform.transportBooking("user2")
            .listBookings(PageRequest.first(10));

        assertThat(user1Bookings.items()).hasSize(1);
        assertThat(user2Bookings.items()).isEmpty();
    }

    @Test
    void transportBookingGetThrowsForUnknown() {
        assertThatThrownBy(() -> platform.transportBooking("user1").getBooking("unknown"))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void accommodationSearchFindsResults() {
        var results = platform.accommodationSearch()
            .search("London", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5),
                2, PageRequest.first(10));
        assertThat(results.items()).isNotEmpty();
        assertThat(results.items()).allSatisfy(acc ->
            assertThat(acc.location().toLowerCase()).contains("london"));
    }

    @Test
    void accommodationSearchPaginates() {
        var page1 = platform.accommodationSearch()
            .search("London", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5),
                2, PageRequest.first(1));
        assertThat(page1.items()).hasSize(1);
        assertThat(page1.hasMore()).isTrue();

        var page2 = platform.accommodationSearch()
            .search("London", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5),
                2, new PageRequest(page1.nextCursor(), 1));
        assertThat(page2.items()).isNotEmpty();
    }

    @Test
    void accommodationDetailReturnsFullInfo() {
        var results = platform.accommodationSearch()
            .search("London", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5),
                2, PageRequest.first(1));
        var accId = results.items().getFirst().id();

        var detail = platform.accommodationSearch().getDetail(accId);
        assertThat(detail.id()).isEqualTo(accId);
        assertThat(detail.name()).isNotBlank();
        assertThat(detail.description()).isNotBlank();
        assertThat(detail.pricePerNight().amount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(detail.amenities()).isNotEmpty();
        assertThat(detail.roomTypes()).isNotEmpty();
    }

    @Test
    void accommodationDetailThrowsForUnknown() {
        assertThatThrownBy(() -> platform.accommodationSearch().getDetail("unknown"))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void accommodationBookingLifecycle() {
        var results = platform.accommodationSearch()
            .search("London", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5),
                2, PageRequest.first(1));
        var acc = results.items().getFirst();

        var detail = platform.accommodationSearch().getDetail(acc.id());
        var roomType = detail.roomTypes().getFirst().name();

        var confirmation = platform.accommodationBooking("user1")
            .book(new AccommodationBookingRequest(
                acc.id(), roomType,
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5),
                "Jane Doe", "jane@example.com"));
        assertThat(confirmation.bookingReference()).isNotBlank();
        assertThat(confirmation.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmation.accommodationId()).isEqualTo(acc.id());
        assertThat(confirmation.guestName()).isEqualTo("Jane Doe");
        assertThat(confirmation.totalPrice().amount()).isGreaterThan(BigDecimal.ZERO);

        var retrieved = platform.accommodationBooking("user1")
            .getBooking(confirmation.bookingReference());
        assertThat(retrieved.bookingReference()).isEqualTo(confirmation.bookingReference());

        var cancelled = platform.accommodationBooking("user1")
            .cancel(confirmation.bookingReference());
        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void accommodationBookingsAreUserScoped() {
        var results = platform.accommodationSearch()
            .search("London", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5),
                2, PageRequest.first(1));
        var acc = results.items().getFirst();
        var detail = platform.accommodationSearch().getDetail(acc.id());
        var roomType = detail.roomTypes().getFirst().name();

        platform.accommodationBooking("user1")
            .book(new AccommodationBookingRequest(
                acc.id(), roomType,
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5),
                "Jane Doe", "jane@example.com"));

        var user1Bookings = platform.accommodationBooking("user1")
            .listBookings(PageRequest.first(10));
        var user2Bookings = platform.accommodationBooking("user2")
            .listBookings(PageRequest.first(10));

        assertThat(user1Bookings.items()).hasSize(1);
        assertThat(user2Bookings.items()).isEmpty();
    }

    @Test
    void accommodationBookingGetThrowsForUnknown() {
        assertThatThrownBy(() -> platform.accommodationBooking("user1").getBooking("unknown"))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }
}
