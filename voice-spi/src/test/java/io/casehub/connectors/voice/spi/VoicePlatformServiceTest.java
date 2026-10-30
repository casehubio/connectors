package io.casehub.connectors.voice.spi;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoicePlatformServiceTest {

    @Test
    void retrievesByIdAndReportsSupport() {
        var noop = new NoOpVoicePlatform();
        var service = new VoicePlatformService(List.of(noop));
        assertThat(service.supports("none")).isTrue();
        assertThat(service.platform("none")).isSameAs(noop);
        assertThat(service.ids()).containsExactly("none");
    }

    @Test
    void throwsForUnknownId() {
        var service = new VoicePlatformService(List.of(new NoOpVoicePlatform()));
        assertThatThrownBy(() -> service.platform("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No voice platform: unknown");
    }
}
