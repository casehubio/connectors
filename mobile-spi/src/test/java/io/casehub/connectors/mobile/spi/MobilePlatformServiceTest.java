package io.casehub.connectors.mobile.spi;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.mobile.model.SendResult;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MobilePlatformServiceTest {

    @Test
    void registersNonNonePlatforms() {
        var testPlatform = createTestPlatform("test-provider");
        var service = new MobilePlatformService(instanceOf(new NoOpMobilePlatform(), testPlatform));

        assertThat(service.ids()).containsExactly("test-provider");
        assertThat(service.supports("test-provider")).isTrue();
        assertThat(service.supports("none")).isFalse();
    }

    @Test
    void unknownId_throws() {
        var service = new MobilePlatformService(instanceOf(new NoOpMobilePlatform()));

        assertThatThrownBy(() -> service.platform("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown mobile platform: unknown");
    }

    @Test
    void platform_returnsRegisteredPlatform() {
        var testPlatform = createTestPlatform("dial");
        var service = new MobilePlatformService(instanceOf(new NoOpMobilePlatform(), testPlatform));

        assertThat(service.platform("dial")).isSameAs(testPlatform);
    }

    private MobilePlatform createTestPlatform(String id) {
        return MobilePlatform.builder(id)
                .sms(new MobilePlatform.Sms() {
                    @Override public SendResult send(String to, String body) { return SendResult.success("id"); }
                    @Override public SendResult send(String to, String body, io.casehub.connectors.mobile.model.SmsOptions o) { return send(to, body); }
                    @Override public Page<io.casehub.connectors.mobile.model.SmsMessage> listMessages(PageRequest p) { return Page.of(List.of()); }
                })
                .build();
    }

    @SuppressWarnings("unchecked")
    private Instance<MobilePlatform> instanceOf(MobilePlatform... platforms) {
        return new Instance<>() {
            @Override public Instance<MobilePlatform> select(java.lang.annotation.Annotation... qualifiers) { return this; }
            @Override public <U extends MobilePlatform> Instance<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) { return (Instance<U>) this; }
            @Override public <U extends MobilePlatform> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) { return (Instance<U>) this; }
            @Override public boolean isUnsatisfied() { return false; }
            @Override public boolean isAmbiguous() { return false; }
            @Override public boolean isResolvable() { return true; }
            @Override public MobilePlatform get() { return platforms[0]; }
            @Override public void destroy(MobilePlatform instance) {}
            @Override public Handle<MobilePlatform> getHandle() { return null; }
            @Override public Iterable<? extends Handle<MobilePlatform>> handles() { return List.of(); }
            @Override public Iterator<MobilePlatform> iterator() { return List.of(platforms).iterator(); }
            @Override public Stream<MobilePlatform> stream() { return Stream.of(platforms); }
        };
    }
}
