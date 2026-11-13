package io.casehub.connectors.mobile.twilio;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.mobile.model.CallStatus;
import io.casehub.connectors.mobile.model.ConversationHandler;
import io.casehub.connectors.mobile.model.ConversationResponse;
import io.casehub.connectors.mobile.model.MessageDirection;
import io.casehub.connectors.mobile.model.MessageStatus;
import io.casehub.connectors.mobile.model.NumberCapability;
import io.casehub.connectors.mobile.model.NumberType;
import io.casehub.connectors.mobile.model.TranscriptTurn;
import io.casehub.connectors.mobile.spi.MobilePlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwilioMobilePlatformTest {

    private static final ObjectMapper MAPPER = TwilioClient.MAPPER;

    @Test
    void platformIdIsTwilio() {
        var platform = createPlatform();
        assertThat(platform.id()).isEqualTo("twilio");
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
    void platformManagedCallThrowsUnsupportedCapability() {
        var platform = createPlatform();
        var handler = new ConversationHandler() {
            @Override
            public ConversationResponse onHumanSpeech(String text, List<TranscriptTurn> t) {
                return new ConversationResponse("OK");
            }
        };

        assertThatThrownBy(() -> platform.voiceCall().initiate("+447700900000", handler))
                .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void mapSmsMessageParsesAllFields() throws Exception {
        var json = MAPPER.readTree("""
                {
                  "sid": "SM1234567890",
                  "from": "+441234567001",
                  "to": "+449876543210",
                  "body": "Hello from Twilio",
                  "status": "delivered",
                  "direction": "outbound-api",
                  "date_sent": "2026-11-01T09:00:00Z"
                }
                """);

        var sms = TwilioMobilePlatform.mapSmsMessage(json);

        assertThat(sms.id()).isEqualTo("SM1234567890");
        assertThat(sms.from()).isEqualTo("+441234567001");
        assertThat(sms.to()).isEqualTo("+449876543210");
        assertThat(sms.body()).isEqualTo("Hello from Twilio");
        assertThat(sms.status()).isEqualTo(MessageStatus.DELIVERED);
        assertThat(sms.direction()).isEqualTo(MessageDirection.OUTBOUND);
        assertThat(sms.timestamp()).isNotNull();
    }

    @Test
    void mapCallParsesAllFields() throws Exception {
        var json = MAPPER.readTree("""
                {
                  "sid": "CA1234567890",
                  "to": "+449876543210",
                  "from": "+441234567001",
                  "status": "completed",
                  "duration": 45,
                  "start_time": "2026-11-01T10:00:00Z",
                  "end_time": "2026-11-01T10:00:45Z"
                }
                """);

        var call = TwilioMobilePlatform.mapCall(json);

        assertThat(call.id()).isEqualTo("CA1234567890");
        assertThat(call.to()).isEqualTo("+449876543210");
        assertThat(call.from()).isEqualTo("+441234567001");
        assertThat(call.status()).isEqualTo(CallStatus.COMPLETED);
        assertThat(call.duration().getSeconds()).isEqualTo(45);
        assertThat(call.startedAt()).isNotNull();
        assertThat(call.endedAt()).isNotNull();
    }

    @Test
    void mapPhoneNumberExtractsCapabilities() throws Exception {
        var json = MAPPER.readTree("""
                {
                  "sid": "PN1234567890",
                  "phone_number": "+441234567001",
                  "capabilities": {
                    "sms": true,
                    "voice": true,
                    "mms": false
                  }
                }
                """);

        var pn = TwilioMobilePlatform.mapPhoneNumber(json);

        assertThat(pn.id()).isEqualTo("PN1234567890");
        assertThat(pn.number()).isEqualTo("+441234567001");
        assertThat(pn.capabilities()).containsExactlyInAnyOrder(
                NumberCapability.SMS, NumberCapability.VOICE);
    }

    @Test
    void mapLookupResultExtractsCarrierInfo() throws Exception {
        var json = MAPPER.readTree("""
                {
                  "phone_number": "+447700900000",
                  "country_code": "GB",
                  "carrier": {
                    "name": "Vodafone",
                    "type": "mobile"
                  }
                }
                """);

        var result = TwilioMobilePlatform.mapLookupResult(json);

        assertThat(result.number()).isEqualTo("+447700900000");
        assertThat(result.carrier()).isEqualTo("Vodafone");
        assertThat(result.countryCode()).isEqualTo("GB");
        assertThat(result.numberType()).isEqualTo(NumberType.MOBILE);
        assertThat(result.capabilities()).containsExactlyInAnyOrder(
                NumberCapability.SMS, NumberCapability.VOICE);
    }

    @Test
    void mapLookupResultLandlineHasVoiceOnly() throws Exception {
        var json = MAPPER.readTree("""
                {
                  "phone_number": "+442071234567",
                  "country_code": "GB",
                  "carrier": {
                    "name": "BT",
                    "type": "landline"
                  }
                }
                """);

        var result = TwilioMobilePlatform.mapLookupResult(json);

        assertThat(result.numberType()).isEqualTo(NumberType.LANDLINE);
        assertThat(result.capabilities()).containsExactly(NumberCapability.VOICE);
    }

    @ParameterizedTest
    @CsvSource({
            "queued, QUEUED",
            "ringing, RINGING",
            "in-progress, IN_PROGRESS",
            "completed, COMPLETED",
            "failed, FAILED",
            "busy, BUSY",
            "no-answer, NO_ANSWER",
            "canceled, CANCELLED"
    })
    void mapCallStatusMapsAllVariants(String input, CallStatus expected) {
        assertThat(TwilioMobilePlatform.mapCallStatus(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "sent, SENT",
            "delivered, DELIVERED",
            "undelivered, UNDELIVERED",
            "failed, FAILED",
            "received, RECEIVED",
            "queued, SENT",
            "accepted, SENT",
            "sending, SENT"
    })
    void mapMessageStatusMapsAllVariants(String input, MessageStatus expected) {
        assertThat(TwilioMobilePlatform.mapMessageStatus(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "outbound-api, OUTBOUND",
            "outbound-call, OUTBOUND",
            "inbound, INBOUND",
            "outbound-reply, OUTBOUND"
    })
    void mapDirectionMapsAllVariants(String input, MessageDirection expected) {
        assertThat(TwilioMobilePlatform.mapDirection(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "mobile, MOBILE",
            "landline, LANDLINE",
            "voip, VOIP"
    })
    void mapNumberTypeMapsAllVariants(String input, NumberType expected) {
        assertThat(TwilioMobilePlatform.mapNumberType(input)).isEqualTo(expected);
    }

    private TwilioMobilePlatform createPlatform() {
        return new TwilioMobilePlatform(
                new TwilioClient("test-sid", "test-token", "http://localhost:0"),
                "+441234567001");
    }
}
