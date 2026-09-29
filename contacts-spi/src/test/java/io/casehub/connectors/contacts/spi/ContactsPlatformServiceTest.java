package io.casehub.connectors.contacts.spi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactsPlatformServiceTest {

    @Test
    void registersAndRetrieves() {
        var noop = new NoOpContactsPlatform();
        var service = new ContactsPlatformService(List.of(noop));
        assertThat(service.platform("none")).isSameAs(noop);
        assertThat(service.supports("none")).isTrue();
        assertThat(service.ids()).containsExactly("none");
    }

    @Test
    void throwsForUnknownPlatform() {
        var service = new ContactsPlatformService(List.of());
        assertThatThrownBy(() -> service.platform("missing"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No contacts platform");
    }

    @Test
    void noOpPlatformReportsNoCapabilities() {
        var noop = new NoOpContactsPlatform();
        assertThat(noop.id()).isEqualTo("none");
        assertThat(noop.supports(ContactsPlatform.ContactRead.class)).isFalse();
        assertThat(noop.supports(ContactsPlatform.GroupRead.class)).isFalse();
        assertThat(noop.supports(ContactsPlatform.ContactWrite.class)).isFalse();
    }
}
