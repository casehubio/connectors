package io.casehub.connectors.mobile.numbrrs;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.mobile.model.NumberCapability;
import io.casehub.connectors.mobile.spi.MobilePlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class NumbrrsMobilePlatformTest {

    private WireMockServer wm;
    private NumbrrsMobilePlatform platform;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wm.start();
        var client = new NumbrrsClient("test-api-key", "http://localhost:" + wm.port() + "/api/v1");
        platform = new NumbrrsMobilePlatform(client, List.of("+447700100001", "+447700100002"));
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    @Test
    void id_is_numbrrs() {
        assertThat(platform.id()).isEqualTo("numbrrs");
    }

    @Test
    void supports_sms_and_number_info() {
        assertThat(platform.supports(MobilePlatform.Sms.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.NumberInfo.class)).isTrue();
    }

    @Test
    void does_not_support_voice_or_messaging() {
        assertThat(platform.supports(MobilePlatform.VoiceCall.class)).isFalse();
        assertThat(platform.supports(MobilePlatform.VoiceCallControl.class)).isFalse();
        assertThat(platform.supports(MobilePlatform.Voicemail.class)).isFalse();
        assertThat(platform.supports(MobilePlatform.Messaging.class)).isFalse();
    }

    @Test
    void send_sms_returns_failure() {
        var result = platform.sms().send("+447700200001", "Hello");
        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("outbound");
    }

    @Test
    void list_messages_returns_received_sms() {
        wm.stubFor(get(urlPathEqualTo("/api/v1/messages"))
                .willReturn(okJson("""
                    [{"messageId":"msg-1","from":"+447700200001","to":"+447700100001","content":"Verification code: 123456","timestamp":"2026-04-30T12:34:56.000Z"}]
                    """)));

        var page = platform.sms().listMessages(PageRequest.first(10));

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().from()).isEqualTo("+447700200001");
        assertThat(page.items().getFirst().body()).isEqualTo("Verification code: 123456");
        wm.verify(getRequestedFor(urlPathEqualTo("/api/v1/messages"))
                .withHeader("Authorization", equalTo("ApiKey test-api-key")));
    }

    @Test
    void list_numbers_returns_configured_numbers() {
        var numbers = platform.numberInfo().listNumbers();

        assertThat(numbers).hasSize(2);
        assertThat(numbers.get(0).number()).isEqualTo("+447700100001");
        assertThat(numbers.get(0).capabilities()).contains(NumberCapability.SMS);
        assertThat(numbers.get(1).number()).isEqualTo("+447700100002");
    }

    @Test
    void lookup_returns_sms_and_voice_capabilities() {
        var caps = platform.numberInfo().lookup("+447700100001");

        assertThat(caps.capabilities()).contains(NumberCapability.SMS, NumberCapability.VOICE);
        assertThat(caps.carrier()).isEqualTo("Numbrrs");
        assertThat(caps.countryCode()).isEqualTo("GB");
    }
}
