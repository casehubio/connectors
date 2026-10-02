package io.casehub.connectors.project.spi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectPlatformServiceTest {

    @Test
    void findsRegisteredPlatform() {
        var noop = new NoOpProjectPlatform();
        var service = new ProjectPlatformService(List.of(noop));
        assertThat(service.platform("none")).isSameAs(noop);
    }

    @Test
    void throwsForUnknownPlatform() {
        var service = new ProjectPlatformService(List.of());
        assertThatThrownBy(() -> service.platform("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No project platform");
    }

    @Test
    void reportsIds() {
        var noop = new NoOpProjectPlatform();
        var service = new ProjectPlatformService(List.of(noop));
        assertThat(service.ids()).containsExactly("none");
    }

    @Test
    void supportsReturnsTrueForKnownId() {
        var noop = new NoOpProjectPlatform();
        var service = new ProjectPlatformService(List.of(noop));
        assertThat(service.supports("none")).isTrue();
        assertThat(service.supports("unknown")).isFalse();
    }
}
