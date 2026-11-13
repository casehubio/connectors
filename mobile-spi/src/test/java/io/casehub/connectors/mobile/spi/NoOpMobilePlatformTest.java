package io.casehub.connectors.mobile.spi;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.mobile.model.CallStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpMobilePlatformTest {

    private final NoOpMobilePlatform platform = new NoOpMobilePlatform();

    @Test
    void id_returnsNone() {
        assertThat(platform.id()).isEqualTo("none");
    }

    @Test
    void supports_alwaysFalse() {
        assertThat(platform.supports(MobilePlatform.Sms.class)).isFalse();
        assertThat(platform.supports(MobilePlatform.VoiceCall.class)).isFalse();
        assertThat(platform.supports(MobilePlatform.VoiceCallControl.class)).isFalse();
        assertThat(platform.supports(MobilePlatform.Voicemail.class)).isFalse();
        assertThat(platform.supports(MobilePlatform.Messaging.class)).isFalse();
        assertThat(platform.supports(MobilePlatform.NumberInfo.class)).isFalse();
    }

    @Test
    void smsSend_returnsFailure() {
        var result = platform.sms().send("+44123", "hello");
        assertThat(result.ok()).isFalse();
    }

    @Test
    void smsListMessages_returnsEmpty() {
        var page = platform.sms().listMessages(PageRequest.first(10));
        assertThat(page.items()).isEmpty();
    }

    @Test
    void messagingSend_returnsFailure() {
        var result = platform.messaging().send("+44123", "hello");
        assertThat(result.ok()).isFalse();
    }

    @Test
    void voicemailList_returnsEmpty() {
        var page = platform.voicemail().list(PageRequest.first(10));
        assertThat(page.items()).isEmpty();
    }

    @Test
    void numberInfo_returnsEmpty() {
        assertThat(platform.numberInfo().listNumbers()).isEmpty();
    }

    @Test
    void numberInfoLookup_returnsEmptyCapabilities() {
        var caps = platform.numberInfo().lookup("+44123");
        assertThat(caps).isNotNull();
        assertThat(caps.number()).isEqualTo("+44123");
        assertThat(caps.capabilities()).isEmpty();
    }

    @Test
    void voicemailTranscribe_returnsEmptyResult() {
        var result = platform.voicemail().transcribe("vm_001");
        assertThat(result.text()).isEmpty();
    }

    @Test
    void voiceCallInitiate_returnsFailedCall() {
        var call = platform.voiceCall().initiate("+44123", "test instruction");
        assertThat(call).isNotNull();
        assertThat(call.status()).isEqualTo(CallStatus.FAILED);
        assertThat(call.failureReason()).contains("No MobilePlatform configured");
    }

    @Test
    void voiceCallInitiateWithHandler_returnsFailedCall() {
        var call = platform.voiceCall().initiate("+44123", (text, transcript) -> null);
        assertThat(call).isNotNull();
        assertThat(call.status()).isEqualTo(CallStatus.FAILED);
        assertThat(call.failureReason()).contains("No MobilePlatform configured");
    }
}
