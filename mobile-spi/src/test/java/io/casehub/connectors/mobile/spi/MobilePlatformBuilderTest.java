package io.casehub.connectors.mobile.spi;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.mobile.model.CallOptions;
import io.casehub.connectors.mobile.model.CallStatus;
import io.casehub.connectors.mobile.model.ConversationHandler;
import io.casehub.connectors.mobile.model.ConversationResponse;
import io.casehub.connectors.mobile.model.SendResult;
import io.casehub.connectors.mobile.model.TranscriptTurn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MobilePlatformBuilderTest {

    @Test
    void builderWithSmsOnly_supportsOnlySms() {
        var sms = new MobilePlatform.Sms() {
            @Override public SendResult send(String to, String body) { return SendResult.success("id1"); }
            @Override public SendResult send(String to, String body, io.casehub.connectors.mobile.model.SmsOptions options) { return send(to, body); }
            @Override public Page<io.casehub.connectors.mobile.model.SmsMessage> listMessages(PageRequest page) { return Page.of(List.of()); }
        };

        var platform = MobilePlatform.builder("test")
                .sms(sms)
                .build();

        assertThat(platform.id()).isEqualTo("test");
        assertThat(platform.supports(MobilePlatform.Sms.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.VoiceCall.class)).isFalse();
        assertThat(platform.supports(MobilePlatform.VoiceCallControl.class)).isFalse();
        assertThat(platform.supports(MobilePlatform.Voicemail.class)).isFalse();
        assertThat(platform.supports(MobilePlatform.Messaging.class)).isFalse();
        assertThat(platform.supports(MobilePlatform.NumberInfo.class)).isFalse();
    }

    @Test
    void builderWithAllCapabilities_supportsAll() {
        var platform = buildFullPlatform();

        assertThat(platform.supports(MobilePlatform.Sms.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.VoiceCall.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.VoiceCallControl.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.Voicemail.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.Messaging.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.NumberInfo.class)).isTrue();
    }

    @Test
    void unsupportedCapability_returnsDegradationDefault() {
        var platform = MobilePlatform.builder("minimal").build();

        var result = platform.sms().send("+44123", "hello");
        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("No MobilePlatform configured");
    }

    @Test
    void smsSend_delegatesToProvidedCapability() {
        var sms = new MobilePlatform.Sms() {
            @Override public SendResult send(String to, String body) { return SendResult.success("msg_123"); }
            @Override public SendResult send(String to, String body, io.casehub.connectors.mobile.model.SmsOptions options) { return send(to, body); }
            @Override public Page<io.casehub.connectors.mobile.model.SmsMessage> listMessages(PageRequest page) { return Page.of(List.of()); }
        };

        var platform = MobilePlatform.builder("test").sms(sms).build();
        var result = platform.sms().send("+44123", "hello");

        assertThat(result.ok()).isTrue();
        assertThat(result.messageId()).isEqualTo("msg_123");
    }

    private MobilePlatform buildFullPlatform() {
        return MobilePlatform.builder("full")
                .sms(NoOpMobilePlatform.NO_OP_SMS)
                .voiceCall(NoOpMobilePlatform.NO_OP_VOICE_CALL)
                .voiceCallControl(NoOpMobilePlatform.NO_OP_VOICE_CALL_CONTROL)
                .voicemail(NoOpMobilePlatform.NO_OP_VOICEMAIL)
                .messaging(NoOpMobilePlatform.NO_OP_MESSAGING)
                .numberInfo(NoOpMobilePlatform.NO_OP_NUMBER_INFO)
                .build();
    }
}
