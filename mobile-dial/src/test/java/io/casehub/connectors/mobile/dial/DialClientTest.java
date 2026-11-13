package io.casehub.connectors.mobile.dial;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.connectors.mobile.dial.model.DialCall;
import io.casehub.connectors.mobile.dial.model.DialLookupResult;
import io.casehub.connectors.mobile.dial.model.DialMessage;
import io.casehub.connectors.mobile.dial.model.DialNumber;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DialClientTest {

    private static final ObjectMapper MAPPER = DialClient.MAPPER;

    @Test
    void mapDialCallParsesAllFields() throws Exception {
        var json = MAPPER.readTree("""
                {
                  "id": "call_abc123",
                  "to": "+447700900000",
                  "from": "+441234567001",
                  "fromNumberId": "pn_ref_001",
                  "status": "completed",
                  "outboundInstruction": "Book a table for 2",
                  "language": "en-GB",
                  "transcript": "Agent: Hello\\nHuman: Hi there",
                  "transcriptTurns": [
                    {"speaker": "agent", "text": "Hello", "startMs": 0, "endMs": 1200},
                    {"speaker": "human", "text": "Hi there", "startMs": 1300, "endMs": 2500}
                  ],
                  "duration": 120,
                  "startedAt": "2026-11-01T10:00:00Z",
                  "endedAt": "2026-11-01T10:02:00Z",
                  "failureReason": null
                }
                """);

        var call = DialClient.mapDialCall(json);

        assertThat(call.id()).isEqualTo("call_abc123");
        assertThat(call.to()).isEqualTo("+447700900000");
        assertThat(call.from()).isEqualTo("+441234567001");
        assertThat(call.status()).isEqualTo("completed");
        assertThat(call.language()).isEqualTo("en-GB");
        assertThat(call.duration()).isEqualTo(120);
        assertThat(call.transcriptTurns()).hasSize(2);
        assertThat(call.transcriptTurns().get(0).speaker()).isEqualTo("agent");
        assertThat(call.transcriptTurns().get(0).text()).isEqualTo("Hello");
        assertThat(call.transcriptTurns().get(1).speaker()).isEqualTo("human");
        assertThat(call.failureReason()).isNull();
    }

    @Test
    void mapDialCallHandlesMinimalFields() throws Exception {
        var json = MAPPER.readTree("""
                {
                  "id": "call_min",
                  "to": "+447700900000",
                  "status": "queued"
                }
                """);

        var call = DialClient.mapDialCall(json);

        assertThat(call.id()).isEqualTo("call_min");
        assertThat(call.to()).isEqualTo("+447700900000");
        assertThat(call.status()).isEqualTo("queued");
        assertThat(call.from()).isNull();
        assertThat(call.transcriptTurns()).isNull();
        assertThat(call.duration()).isNull();
    }

    @Test
    void mapDialMessageParsesAllFields() throws Exception {
        var json = MAPPER.readTree("""
                {
                  "id": "msg_xyz789",
                  "to": "+449876543210",
                  "from": "+441234567001",
                  "body": "Your booking is confirmed.",
                  "channel": "sms",
                  "status": "delivered",
                  "direction": "outbound",
                  "timestamp": "2026-11-01T09:00:00Z",
                  "fromNumberId": "pn_ref_001",
                  "media": [
                    {"url": "https://cdn.dial.ai/img.jpg", "contentType": "image/jpeg", "sizeBytes": 12345}
                  ]
                }
                """);

        var msg = DialClient.mapDialMessage(json);

        assertThat(msg.id()).isEqualTo("msg_xyz789");
        assertThat(msg.to()).isEqualTo("+449876543210");
        assertThat(msg.body()).isEqualTo("Your booking is confirmed.");
        assertThat(msg.channel()).isEqualTo("sms");
        assertThat(msg.status()).isEqualTo("delivered");
        assertThat(msg.direction()).isEqualTo("outbound");
        assertThat(msg.media()).hasSize(1);
        assertThat(msg.media().get(0).url()).isEqualTo("https://cdn.dial.ai/img.jpg");
    }

    @Test
    void mapDialNumberParsesAllFields() throws Exception {
        var json = MAPPER.readTree("""
                {
                  "id": "pn_dial_001",
                  "number": "+441234567001",
                  "capabilities": ["sms", "voice", "whatsapp"],
                  "status": "ready"
                }
                """);

        var number = DialClient.mapDialNumber(json);

        assertThat(number.id()).isEqualTo("pn_dial_001");
        assertThat(number.number()).isEqualTo("+441234567001");
        assertThat(number.capabilities()).containsExactlyInAnyOrder("sms", "voice", "whatsapp");
        assertThat(number.status()).isEqualTo("ready");
    }

    @Test
    void mapDialLookupParsesAllFields() throws Exception {
        var json = MAPPER.readTree("""
                {
                  "number": "+447700900000",
                  "carrier": "Vodafone UK",
                  "capabilities": ["sms", "voice"],
                  "countryCode": "GB",
                  "numberType": "mobile"
                }
                """);

        var lookup = DialClient.mapDialLookup(json);

        assertThat(lookup.number()).isEqualTo("+447700900000");
        assertThat(lookup.carrier()).isEqualTo("Vodafone UK");
        assertThat(lookup.capabilities()).containsExactlyInAnyOrder("sms", "voice");
        assertThat(lookup.countryCode()).isEqualTo("GB");
        assertThat(lookup.numberType()).isEqualTo("mobile");
    }

    @Test
    void mapDialCallIgnoresUnknownFields() throws Exception {
        var json = MAPPER.readTree("""
                {
                  "id": "call_unk",
                  "to": "+447700900000",
                  "status": "completed",
                  "someNewField": "should be ignored",
                  "nestedNewField": {"deep": true}
                }
                """);

        var call = DialClient.mapDialCall(json);

        assertThat(call.id()).isEqualTo("call_unk");
        assertThat(call.status()).isEqualTo("completed");
    }
}
