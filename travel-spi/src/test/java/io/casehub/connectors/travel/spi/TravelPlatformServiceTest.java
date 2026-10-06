package io.casehub.connectors.travel.spi;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TravelPlatformServiceTest {

    private TravelPlatformService service;

    @BeforeEach
    void setUp() {
        service = new TravelPlatformService(List.of(new NoOpTravelPlatform()));
    }

    @Test
    void lookupByIdReturnsRegisteredPlatform() {
        var platform = service.platform("none");
        assertThat(platform.id()).isEqualTo("none");
    }

    @Test
    void lookupByUnknownIdThrows() {
        assertThatThrownBy(() -> service.platform("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown");
    }

    @Test
    void supportsReturnsTrueForRegistered() {
        assertThat(service.supports("none")).isTrue();
    }

    @Test
    void supportsReturnsFalseForUnknown() {
        assertThat(service.supports("unknown")).isFalse();
    }

    @Test
    void idsReturnsAllRegistered() {
        assertThat(service.ids()).containsExactly("none");
    }
}
