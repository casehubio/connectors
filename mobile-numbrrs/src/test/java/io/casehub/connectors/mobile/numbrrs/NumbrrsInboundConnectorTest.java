package io.casehub.connectors.mobile.numbrrs;

import io.casehub.connectors.HttpMethod;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NumbrrsInboundConnectorTest {

    private static final String SECRET = "test-signing-secret";
    private final NumbrrsInboundConnector connector = new NumbrrsInboundConnector(SECRET);

    @Test
    void id_is_numbrrs_sms_inbound() {
        assertThat(connector.id()).isEqualTo("numbrrs-sms-inbound");
    }

    @Test
    void sms_received_delivers_message() {
        var body = """
                {"event":"sms.received","messageId":"msg-123","from":"+447700100001","to":"+447700100002","content":"Hello from Signal","country":"GB","countryName":"United Kingdom","timestamp":"2026-04-30T12:34:56.000Z"}""";
        var request = signedRequest(body);

        var result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        assertThat(delivered.messages()).hasSize(1);
        var msg = delivered.messages().getFirst();
        assertThat(msg.connectorType()).isEqualTo("sms");
        assertThat(msg.externalSenderId()).isEqualTo("+447700100001");
        assertThat(msg.externalChannelRef()).isEqualTo("+447700100002");
        assertThat(msg.content()).isEqualTo("Hello from Signal");
        assertThat(msg.metadata()).containsEntry("message-id", "msg-123");
    }

    @Test
    void voice_received_delivers_transcription() {
        var body = """
                {"event":"voice.received","from":"+447700200001","to":"+447700100002","transcription":"Please call me back","callId":"call-456","durationSeconds":45,"recordingUrl":"https://sms.numbrrs.com/recordings/call-456.wav"}""";
        var request = signedRequest(body);

        var result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        var msg = delivered.messages().getFirst();
        assertThat(msg.connectorType()).isEqualTo("voice");
        assertThat(msg.content()).isEqualTo("Please call me back");
        assertThat(msg.metadata()).containsEntry("call-id", "call-456");
        assertThat(msg.metadata()).containsEntry("duration-seconds", "45");
        assertThat(msg.metadata()).containsEntry("recording-url", "https://sms.numbrrs.com/recordings/call-456.wav");
    }

    @Test
    void voice_without_transcription_uses_fallback_message() {
        var body = """
                {"event":"voice.received","from":"+447700200001","to":"+447700100002","transcription":"","callId":"call-789","durationSeconds":10}""";
        var request = signedRequest(body);

        var result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var msg = ((WebhookResult.Delivered) result).messages().getFirst();
        assertThat(msg.content()).startsWith("Voice call from +447700200001");
    }

    @Test
    void invalid_signature_returns_unauthorized() {
        var body = """
                {"event":"sms.received","from":"+447700100001","to":"+447700100002","content":"test"}""";
        var ts = String.valueOf(Instant.now().getEpochSecond());
        var request = new WebhookRequest(body,
                Map.of("x-numbrrs-signature", List.of("sha256=invalid"),
                        "x-numbrrs-timestamp", List.of(ts)),
                Map.of(), HttpMethod.POST, "/connectors/numbrrs-sms-inbound/webhook");

        var result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Unauthorized.class);
    }

    @Test
    void missing_signature_returns_unauthorized() {
        var body = """
                {"event":"sms.received","from":"+447700100001","to":"+447700100002","content":"test"}""";
        var request = new WebhookRequest(body, Map.of(), Map.of(),
                HttpMethod.POST, "/connectors/numbrrs-sms-inbound/webhook");

        var result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Unauthorized.class);
    }

    @Test
    void expired_timestamp_returns_unauthorized() {
        var body = """
                {"event":"sms.received","from":"+447700100001","to":"+447700100002","content":"test"}""";
        var oldTs = String.valueOf(Instant.now().getEpochSecond() - 600);
        var sig = "sha256=" + NumbrrsInboundConnector.hmacSha256(SECRET, oldTs + "." + body);
        var request = new WebhookRequest(body,
                Map.of("x-numbrrs-signature", List.of(sig),
                        "x-numbrrs-timestamp", List.of(oldTs)),
                Map.of(), HttpMethod.POST, "/connectors/numbrrs-sms-inbound/webhook");

        var result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Unauthorized.class);
    }

    @Test
    void unknown_event_type_returns_ignored() {
        var body = """
                {"event":"number.provisioned","number":"+447700100001"}""";
        var request = signedRequest(body);

        var result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Ignored.class);
    }

    @Test
    void blank_secret_returns_ignored() {
        var unconfigured = new NumbrrsInboundConnector("");
        var body = """
                {"event":"sms.received","from":"+447700100001","to":"+447700100002","content":"test"}""";
        var request = new WebhookRequest(body, Map.of(), Map.of(),
                HttpMethod.POST, "/connectors/numbrrs-sms-inbound/webhook");

        var result = unconfigured.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Ignored.class);
    }

    @Test
    void hmac_sha256_produces_correct_signature() {
        var sig = NumbrrsInboundConnector.hmacSha256("secret", "timestamp.payload");
        assertThat(sig).hasSize(64);
        assertThat(sig).matches("[0-9a-f]+");
    }

    private WebhookRequest signedRequest(String body) {
        var ts = String.valueOf(Instant.now().getEpochSecond());
        var sig = "sha256=" + NumbrrsInboundConnector.hmacSha256(SECRET, ts + "." + body);
        return new WebhookRequest(body,
                Map.of("x-numbrrs-signature", List.of(sig),
                        "x-numbrrs-timestamp", List.of(ts)),
                Map.of(), HttpMethod.POST, "/connectors/numbrrs-sms-inbound/webhook");
    }
}
