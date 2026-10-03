package io.casehub.connectors.commerce.spi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommercePlatformServiceTest {

    @Test
    void platformLooksUpById() {
        var noop = new NoOpCommercePlatform();
        var service = new CommercePlatformService(List.of(noop));
        assertThat(service.platform("none").id()).isEqualTo("none");
    }

    @Test
    void unknownIdThrows() {
        var service = new CommercePlatformService(List.of(new NoOpCommercePlatform()));
        assertThatThrownBy(() -> service.platform("missing"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing");
    }

    @Test
    void idsReturnsRegisteredPlatforms() {
        var service = new CommercePlatformService(List.of(new NoOpCommercePlatform()));
        assertThat(service.ids()).containsExactly("none");
    }

    @Test
    void supportsChecksExistence() {
        var service = new CommercePlatformService(List.of(new NoOpCommercePlatform()));
        assertThat(service.supports("none")).isTrue();
        assertThat(service.supports("missing")).isFalse();
    }
}
