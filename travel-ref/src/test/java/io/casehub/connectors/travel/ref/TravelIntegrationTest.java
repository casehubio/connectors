package io.casehub.connectors.travel.ref;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.travel.model.AccommodationBookingRequest;
import io.casehub.connectors.travel.model.BookingStatus;
import io.casehub.connectors.travel.model.TransportBookingRequest;
import io.casehub.connectors.travel.model.TransportMode;
import io.casehub.connectors.travel.spi.TravelPlatform;
import io.casehub.connectors.travel.spi.TravelPlatformService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class TravelIntegrationTest {

    @Inject
    TravelPlatformService service;

    @Inject
    TravelPlatform platform;

    @Test
    void serviceDiscoversRefPlatform() {
        assertThat(service.ids()).contains("ref");
        assertThat(service.platform("ref").id()).isEqualTo("ref");
    }

    @Test
    void injectedPlatformIsRef() {
        assertThat(platform.id()).isEqualTo("ref");
        assertThat(platform.supports(TravelPlatform.TransportSearch.class)).isTrue();
        assertThat(platform.supports(TravelPlatform.AccommodationBooking.class)).isTrue();
    }

    @Test
    void fullLifecycle_searchToBooking() {
        var search = platform.transportSearch();
        var options = search.search("London", "Paris",
            LocalDate.of(2026, 12, 1), PageRequest.first(10));
        assertThat(options.items()).isNotEmpty();
        var option = options.items().getFirst();

        var trainOptions = search.searchByMode("London", "Paris",
            LocalDate.of(2026, 12, 1), TransportMode.TRAIN, PageRequest.first(10));
        assertThat(trainOptions.items()).isNotEmpty();
        assertThat(trainOptions.items()).allSatisfy(opt ->
            assertThat(opt.mode()).isEqualTo(TransportMode.TRAIN));

        var booking = platform.transportBooking("user1")
            .book(new TransportBookingRequest(option.id(), "Jane Doe", "jane@example.com"));
        assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.price().amount()).isGreaterThan(BigDecimal.ZERO);

        var retrieved = platform.transportBooking("user1")
            .getBooking(booking.bookingReference());
        assertThat(retrieved.bookingReference()).isEqualTo(booking.bookingReference());

        var accSearch = platform.accommodationSearch();
        var accommodations = accSearch.search("London",
            LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5), 2, PageRequest.first(10));
        assertThat(accommodations.items()).isNotEmpty();
        var acc = accommodations.items().getFirst();

        var detail = accSearch.getDetail(acc.id());
        assertThat(detail.name()).isEqualTo(acc.name());
        assertThat(detail.roomTypes()).isNotEmpty();

        var accBooking = platform.accommodationBooking("user1")
            .book(new AccommodationBookingRequest(
                acc.id(), detail.roomTypes().getFirst().name(),
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5),
                "Jane Doe", "jane@example.com"));
        assertThat(accBooking.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(accBooking.totalPrice().amount()).isGreaterThan(BigDecimal.ZERO);
    }
}
