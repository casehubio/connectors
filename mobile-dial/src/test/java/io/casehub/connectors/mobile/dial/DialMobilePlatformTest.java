package io.casehub.connectors.mobile.dial;

import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.mobile.dial.model.DialCall;
import io.casehub.connectors.mobile.dial.model.DialLookupResult;
import io.casehub.connectors.mobile.dial.model.DialMediaAttachment;
import io.casehub.connectors.mobile.dial.model.DialMessage;
import io.casehub.connectors.mobile.dial.model.DialNumber;
import io.casehub.connectors.mobile.dial.model.DialTranscriptTurn;
import io.casehub.connectors.mobile.model.CallStatus;
import io.casehub.connectors.mobile.model.MediaAttachment;
import io.casehub.connectors.mobile.model.MessageDirection;
import io.casehub.connectors.mobile.model.MessageStatus;
import io.casehub.connectors.mobile.model.MessagingChannel;
import io.casehub.connectors.mobile.model.NumberCapability;
import io.casehub.connectors.mobile.model.NumberStatus;
import io.casehub.connectors.mobile.model.NumberType;
import io.casehub.connectors.mobile.model.Speaker;
import io.casehub.connectors.mobile.spi.MobilePlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DialMobilePlatformTest {

    @Test
    void mapCallConvertsAllFields() {
        var dialCall = new DialCall("call_1", "+447700900000", "+441234567001", "pn_001",
                "completed", "Book a table", "en-GB", "Agent: Hi\nHuman: Hello",
                List.of(
                        new DialTranscriptTurn("agent", "Hi", 0, 500),
                        new DialTranscriptTurn("human", "Hello", 600, 1200)),
                120, "2026-11-01T10:00:00Z", "2026-11-01T10:02:00Z", null);

        var call = DialMobilePlatform.mapCall(dialCall);

        assertThat(call.id()).isEqualTo("call_1");
        assertThat(call.to()).isEqualTo("+447700900000");
        assertThat(call.from()).isEqualTo("+441234567001");
        assertThat(call.status()).isEqualTo(CallStatus.COMPLETED);
        assertThat(call.transcript()).isEqualTo("Agent: Hi\nHuman: Hello");
        assertThat(call.transcriptTurns()).hasSize(2);
        assertThat(call.transcriptTurns().get(0).speaker()).isEqualTo(Speaker.AGENT);
        assertThat(call.transcriptTurns().get(0).text()).isEqualTo("Hi");
        assertThat(call.transcriptTurns().get(1).speaker()).isEqualTo(Speaker.HUMAN);
        assertThat(call.duration().getSeconds()).isEqualTo(120);
        assertThat(call.startedAt()).isNotNull();
        assertThat(call.endedAt()).isNotNull();
        assertThat(call.failureReason()).isNull();
    }

    @Test
    void mapCallHandlesNullTranscriptTurns() {
        var dialCall = new DialCall("call_2", "+447700900000", null, null,
                "queued", null, null, null, null, null, null, null, null);

        var call = DialMobilePlatform.mapCall(dialCall);

        assertThat(call.id()).isEqualTo("call_2");
        assertThat(call.status()).isEqualTo(CallStatus.QUEUED);
        assertThat(call.transcriptTurns()).isEmpty();
        assertThat(call.duration()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "queued, QUEUED",
            "ringing, RINGING",
            "in_progress, IN_PROGRESS",
            "in-progress, IN_PROGRESS",
            "completed, COMPLETED",
            "ended, COMPLETED",
            "failed, FAILED",
            "busy, BUSY",
            "no_answer, NO_ANSWER",
            "cancelled, CANCELLED",
            "canceled, CANCELLED"
    })
    void mapCallStatusMapsAllVariants(String input, CallStatus expected) {
        assertThat(DialMobilePlatform.mapCallStatus(input)).isEqualTo(expected);
    }

    @Test
    void mapCallStatusDefaultsToQueued() {
        assertThat(DialMobilePlatform.mapCallStatus(null)).isEqualTo(CallStatus.QUEUED);
        assertThat(DialMobilePlatform.mapCallStatus("unknown_status")).isEqualTo(CallStatus.QUEUED);
    }

    @Test
    void mapSendResultSuccessful() {
        var msg = new DialMessage("msg_1", "+447700900000", "+441234567001",
                "Hello", "sms", "sent", "outbound", "2026-11-01T09:00:00Z", null, null);

        var result = DialMobilePlatform.mapSendResult(msg);

        assertThat(result.ok()).isTrue();
        assertThat(result.messageId()).isEqualTo("msg_1");
    }

    @Test
    void mapSendResultFailed() {
        var msg = new DialMessage("msg_2", "+447700900000", "+441234567001",
                "Hello", "sms", "failed", "outbound", "2026-11-01T09:00:00Z", null, null);

        var result = DialMobilePlatform.mapSendResult(msg);

        assertThat(result.ok()).isFalse();
    }

    @Test
    void mapSendResultNull() {
        var result = DialMobilePlatform.mapSendResult(null);
        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("No response");
    }

    @Test
    void mapSmsMessageConvertsAllFields() {
        var msg = new DialMessage("msg_3", "+449876543210", "+441234567001",
                "Hi there", "sms", "delivered", "inbound", "2026-11-01T09:05:00Z", null, null);

        var sms = DialMobilePlatform.mapSmsMessage(msg);

        assertThat(sms.id()).isEqualTo("msg_3");
        assertThat(sms.from()).isEqualTo("+441234567001");
        assertThat(sms.to()).isEqualTo("+449876543210");
        assertThat(sms.body()).isEqualTo("Hi there");
        assertThat(sms.status()).isEqualTo(MessageStatus.DELIVERED);
        assertThat(sms.direction()).isEqualTo(MessageDirection.INBOUND);
        assertThat(sms.timestamp()).isNotNull();
    }

    @Test
    void mapRichMessageConvertsWithMedia() {
        var media = List.of(
                new DialMediaAttachment("https://cdn.dial.ai/img.jpg", "image/jpeg", 12345));
        var msg = new DialMessage("msg_4", "+449876543210", "+441234567001",
                "Photo", "whatsapp", "sent", "outbound", "2026-11-01T09:00:00Z", null, media);

        var rich = DialMobilePlatform.mapRichMessage(msg);

        assertThat(rich.id()).isEqualTo("msg_4");
        assertThat(rich.channel()).isEqualTo(MessagingChannel.WHATSAPP);
        assertThat(rich.media()).hasSize(1);
        assertThat(rich.media().get(0).url()).isEqualTo("https://cdn.dial.ai/img.jpg");
        assertThat(rich.media().get(0).contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void mapPhoneNumberConvertsCapabilities() {
        var dn = new DialNumber("pn_1", "+441234567001", Set.of("sms", "voice", "whatsapp"), "ready");

        var pn = DialMobilePlatform.mapPhoneNumber(dn);

        assertThat(pn.id()).isEqualTo("pn_1");
        assertThat(pn.number()).isEqualTo("+441234567001");
        assertThat(pn.capabilities()).containsExactlyInAnyOrder(
                NumberCapability.SMS, NumberCapability.VOICE, NumberCapability.WHATSAPP);
        assertThat(pn.status()).isEqualTo(NumberStatus.READY);
    }

    @Test
    void mapLookupResultConvertsAllFields() {
        var dl = new DialLookupResult("+447700900000", "Vodafone UK",
                Set.of("sms", "voice"), "GB", "mobile");

        var result = DialMobilePlatform.mapLookupResult(dl);

        assertThat(result.number()).isEqualTo("+447700900000");
        assertThat(result.carrier()).isEqualTo("Vodafone UK");
        assertThat(result.capabilities()).containsExactlyInAnyOrder(
                NumberCapability.SMS, NumberCapability.VOICE);
        assertThat(result.countryCode()).isEqualTo("GB");
        assertThat(result.numberType()).isEqualTo(NumberType.MOBILE);
    }

    @ParameterizedTest
    @CsvSource({
            "sent, SENT",
            "delivered, DELIVERED",
            "undelivered, UNDELIVERED",
            "failed, FAILED",
            "received, RECEIVED"
    })
    void mapMessageStatusMapsAllVariants(String input, MessageStatus expected) {
        assertThat(DialMobilePlatform.mapMessageStatus(input)).isEqualTo(expected);
    }

    @Test
    void mapMessageStatusDefaultsToUnknown() {
        assertThat(DialMobilePlatform.mapMessageStatus(null)).isEqualTo(MessageStatus.UNKNOWN);
        assertThat(DialMobilePlatform.mapMessageStatus("something")).isEqualTo(MessageStatus.UNKNOWN);
    }

    @ParameterizedTest
    @CsvSource({
            "agent, AGENT",
            "assistant, AGENT",
            "ai, AGENT",
            "human, HUMAN",
            "user, HUMAN",
            "transfer, TRANSFER_TARGET",
            "transfer_target, TRANSFER_TARGET"
    })
    void mapSpeakerMapsAllVariants(String input, Speaker expected) {
        assertThat(DialMobilePlatform.mapSpeaker(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "whatsapp, WHATSAPP",
            "imessage, IMESSAGE",
            "rcs, RCS"
    })
    void mapMessagingChannelMapsAllVariants(String input, MessagingChannel expected) {
        assertThat(DialMobilePlatform.mapMessagingChannel(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "provisioning, PROVISIONING",
            "ready, READY",
            "active, READY",
            "failed, FAILED"
    })
    void mapNumberStatusMapsAllVariants(String input, NumberStatus expected) {
        assertThat(DialMobilePlatform.mapNumberStatus(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "mobile, MOBILE",
            "landline, LANDLINE",
            "voip, VOIP"
    })
    void mapNumberTypeMapsAllVariants(String input, NumberType expected) {
        assertThat(DialMobilePlatform.mapNumberType(input)).isEqualTo(expected);
    }

    @Test
    void platformIdIsDial() {
        var platform = createPlatform();
        assertThat(platform.id()).isEqualTo("dial");
    }

    @Test
    void supportsSmsVoiceCallVoiceCallControlMessagingNumberInfo() {
        var platform = createPlatform();
        assertThat(platform.supports(MobilePlatform.Sms.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.VoiceCall.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.VoiceCallControl.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.Messaging.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.NumberInfo.class)).isTrue();
    }

    @Test
    void doesNotSupportVoicemail() {
        var platform = createPlatform();
        assertThat(platform.supports(MobilePlatform.Voicemail.class)).isFalse();
    }

    @Test
    void transferThrowsUnsupportedCapabilityException() {
        var platform = createPlatform();
        assertThatThrownBy(() -> platform.voiceCallControl().transfer("call_1", "+447700900000"))
                .isInstanceOf(UnsupportedCapabilityException.class);
    }

    private DialMobilePlatform createPlatform() {
        return new DialMobilePlatform(
                new DialClient("test-key", "http://localhost:0"),
                new DialWebSocket("test-key", "ws://localhost:0"));
    }
}
