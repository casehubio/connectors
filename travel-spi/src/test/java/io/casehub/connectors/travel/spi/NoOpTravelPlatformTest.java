package io.casehub.connectors.travel.spi;

import java.time.LocalDate;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.travel.model.AccommodationBookingRequest;
import io.casehub.connectors.travel.model.TransportBookingRequest;
import io.casehub.connectors.travel.model.TransportMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpTravelPlatformTest {

    private NoOpTravelPlatform platform;

    @BeforeEach
    void setUp() {
        platform = new NoOpTravelPlatform();
    }

    @Test
    void idReturnsNone() {
        assertThat(platform.id()).isEqualTo("none");
    }

    @Test
    void supportsReturnsFalseForAllCapabilities() {
        assertThat(platform.supports(TravelPlatform.TransportSearch.class)).isFalse();
        assertThat(platform.supports(TravelPlatform.TransportBooking.class)).isFalse();
        assertThat(platform.supports(TravelPlatform.AccommodationSearch.class)).isFalse();
        assertThat(platform.supports(TravelPlatform.AccommodationBooking.class)).isFalse();
    }

    @Test
    void transportSearchThrowsUnsupported() {
        var search = platform.transportSearch();
        assertThatThrownBy(() -> search.search("London", "Paris",
            LocalDate.of(2026, 12, 1), PageRequest.first(10)))
            .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void transportSearchByModeThrowsUnsupported() {
        var search = platform.transportSearch();
        assertThatThrownBy(() -> search.searchByMode("London", "Paris",
            LocalDate.of(2026, 12, 1), TransportMode.TRAIN, PageRequest.first(10)))
            .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void transportBookingThrowsUnsupported() {
        var booking = platform.transportBooking("user1");
        assertThatThrownBy(() -> booking.book(
            new TransportBookingRequest("opt-1", "Jane Doe", "jane@example.com")))
            .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void transportBookingGetThrowsUnsupported() {
        var booking = platform.transportBooking("user1");
        assertThatThrownBy(() -> booking.getBooking("ref-1"))
            .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void transportBookingListThrowsUnsupported() {
        var booking = platform.transportBooking("user1");
        assertThatThrownBy(() -> booking.listBookings(PageRequest.first(10)))
            .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void transportBookingCancelThrowsUnsupported() {
        var booking = platform.transportBooking("user1");
        assertThatThrownBy(() -> booking.cancel("ref-1"))
            .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void accommodationSearchThrowsUnsupported() {
        var search = platform.accommodationSearch();
        assertThatThrownBy(() -> search.search("London",
            LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5), 2, PageRequest.first(10)))
            .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void accommodationSearchGetDetailThrowsUnsupported() {
        var search = platform.accommodationSearch();
        assertThatThrownBy(() -> search.getDetail("acc-1"))
            .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void accommodationBookingThrowsUnsupported() {
        var booking = platform.accommodationBooking("user1");
        assertThatThrownBy(() -> booking.book(new AccommodationBookingRequest(
            "acc-1", "Double", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5),
            "Jane Doe", "jane@example.com")))
            .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void accommodationBookingGetThrowsUnsupported() {
        var booking = platform.accommodationBooking("user1");
        assertThatThrownBy(() -> booking.getBooking("ref-1"))
            .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void accommodationBookingListThrowsUnsupported() {
        var booking = platform.accommodationBooking("user1");
        assertThatThrownBy(() -> booking.listBookings(PageRequest.first(10)))
            .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void accommodationBookingCancelThrowsUnsupported() {
        var booking = platform.accommodationBooking("user1");
        assertThatThrownBy(() -> booking.cancel("ref-1"))
            .isInstanceOf(UnsupportedCapabilityException.class);
    }
}
