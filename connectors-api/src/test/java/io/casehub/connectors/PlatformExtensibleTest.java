package io.casehub.connectors;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformExtensibleTest {

    interface TestExtension {
        String extra();
    }

    static class PlainPlatform implements PlatformExtensible {}

    static class ExtendedPlatform implements PlatformExtensible, TestExtension {
        @Override
        public String extra() { return "extended"; }
    }

    @Test
    void as_returnsEmpty_whenNotExtended() {
        PlatformExtensible platform = new PlainPlatform();
        Optional<TestExtension> result = platform.as(TestExtension.class);
        assertThat(result).isEmpty();
    }

    @Test
    void as_returnsPresent_whenExtended() {
        PlatformExtensible platform = new ExtendedPlatform();
        Optional<TestExtension> result = platform.as(TestExtension.class);
        assertThat(result).isPresent();
        assertThat(result.get().extra()).isEqualTo("extended");
    }

    @Test
    void as_returnsEmpty_forUnrelatedType() {
        PlatformExtensible platform = new ExtendedPlatform();
        Optional<Runnable> result = platform.as(Runnable.class);
        assertThat(result).isEmpty();
    }
}
