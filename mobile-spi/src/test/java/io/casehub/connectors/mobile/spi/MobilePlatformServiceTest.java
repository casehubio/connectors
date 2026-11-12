package io.casehub.connectors.mobile.spi;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.mobile.model.SendResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MobilePlatformServiceTest {

    @Test
    void registersNonNonePlatforms() {
        var testPlatform = createTestPlatform("test-provider");
        var service = new MobilePlatformService(List.of(new NoOpMobilePlatform(), testPlatform));

        assertThat(service.ids()).containsExactly("test-provider");
        assertThat(service.supports("test-provider")).isTrue();
        assertThat(service.supports("none")).isFalse();
    }

    @Test
    void unknownId_throws() {
        var service = new MobilePlatformService(List.of(new NoOpMobilePlatform()));

        assertThatThrownBy(() -> service.platform("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown mobile platform: unknown");
    }

    @Test
    void platform_returnsRegisteredPlatform() {
        var testPlatform = createTestPlatform("dial");
        var service = new MobilePlatformService(List.of(new NoOpMobilePlatform(), testPlatform));

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
}
