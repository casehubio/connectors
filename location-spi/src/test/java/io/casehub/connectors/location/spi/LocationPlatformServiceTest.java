package io.casehub.connectors.location.spi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocationPlatformServiceTest {

    @Test
    void registersAndRetrieves() {
        var noop = new NoOpLocationPlatform();
        var service = new LocationPlatformService(List.of(noop));
        assertThat(service.platform("none")).isSameAs(noop);
        assertThat(service.supports("none")).isTrue();
        assertThat(service.ids()).containsExactly("none");
    }

    @Test
    void throwsForUnknownPlatform() {
        var service = new LocationPlatformService(List.of());
        assertThatThrownBy(() -> service.platform("missing"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No location platform");
    }

    @Test
    void noOpPlatformReportsNoCapabilities() {
        var noop = new NoOpLocationPlatform();
        assertThat(noop.id()).isEqualTo("none");
        assertThat(noop.supports(LocationPlatform.PlaceSearch.class)).isFalse();
        assertThat(noop.supports(LocationPlatform.PlaceDetails.class)).isFalse();
        assertThat(noop.supports(LocationPlatform.Geocoding.class)).isFalse();
        assertThat(noop.supports(LocationPlatform.Directions.class)).isFalse();
    }
}
