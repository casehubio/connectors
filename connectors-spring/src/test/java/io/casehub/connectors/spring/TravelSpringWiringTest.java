package io.casehub.connectors.spring;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.travel.model.BookingStatus;
import io.casehub.connectors.travel.model.TransportBookingRequest;
import io.casehub.connectors.travel.model.TransportMode;
import io.casehub.connectors.travel.ref.InMemoryTravelBackend;
import io.casehub.connectors.travel.ref.RefTravelPlatform;
import io.casehub.connectors.travel.spi.TravelPlatform;
import io.casehub.connectors.travel.spi.TravelPlatformService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {
    TravelSpringWiringTest.TestConfig.class,
    TravelRefManualConfig.class
})
class TravelSpringWiringTest {

    @TestConfiguration
    static class TestConfig {

        @Bean
        InMemoryTravelBackend travelBackend() {
            return new InMemoryTravelBackend();
        }

        @Bean
        RefTravelPlatform refTravelPlatform(InMemoryTravelBackend backend) {
            return new RefTravelPlatform(backend);
        }
    }

    @Autowired
    TravelPlatformService service;

    @Autowired
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
    void searchAndBookLifecycle() {
        var options = platform.transportSearch()
            .search("London", "Paris", LocalDate.of(2026, 12, 1), PageRequest.first(10));
        assertThat(options.items()).isNotEmpty();

        var trainOptions = platform.transportSearch()
            .searchByMode("London", "Paris", LocalDate.of(2026, 12, 1),
                TransportMode.TRAIN, PageRequest.first(10));
        assertThat(trainOptions.items()).isNotEmpty();

        var booking = platform.transportBooking("user1")
            .book(new TransportBookingRequest(
                options.items().getFirst().id(), "Jane Doe", "jane@example.com"));
        assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.price().amount()).isGreaterThan(BigDecimal.ZERO);
    }
}
