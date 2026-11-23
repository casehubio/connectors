package io.casehub.connectors.mobile.numbrrs;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.WebhookInboundConnector;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class NumbrrsInboundConnector extends WebhookInboundConnector {

    private static final Logger LOG = Logger.getLogger(NumbrrsInboundConnector.class.getName());
    private static final long MAX_AGE_SECONDS = 5 * 60;

    private final String signingSecret;

    public NumbrrsInboundConnector(String signingSecret) {
        this.signingSecret = signingSecret;
    }

    @Override
    public String id() {
        return InboundConnectorIds.NUMBRRS_SMS;
    }

    @Override
    public WebhookResult handle(WebhookRequest request) {
        try {
            return doHandle(request);
        } catch (Exception e) {
            LOG.severe("Unexpected error in NumbrrsInboundConnector: " + e.getMessage());
            return new WebhookResult.Ignored();
        }
    }

    private WebhookResult doHandle(WebhookRequest request) {
        if (signingSecret.isBlank()) {
            LOG.warning("numbrrs-inbound: signing secret not configured — ignoring request");
            return new WebhookResult.Ignored();
        }

        if (!verifySignature(request)) {
            return new WebhookResult.Unauthorized();
        }

        try {
            var payload = NumbrrsClient.MAPPER.readTree(request.body());
            var event = payload.path("event").asText("");

            return switch (event) {
                case "sms.received" -> handleSms(payload, request.tenancyId());
                case "voice.received" -> handleVoice(payload, request.tenancyId());
                default -> {
                    LOG.fine("numbrrs-inbound: ignoring event type: " + event);
                    yield new WebhookResult.Ignored();
                }
            };
        } catch (Exception e) {
            LOG.warning("numbrrs-inbound: failed to parse payload: " + e.getMessage());
            return new WebhookResult.Ignored();
        }
    }

    private WebhookResult handleSms(JsonNode payload, String tenancyId) {
        var from = payload.path("from").asText(null);
        var to = payload.path("to").asText(null);
        var content = payload.path("content").asText("");
        var messageId = payload.path("messageId").asText(null);

        if (from == null || to == null) {
            LOG.warning("numbrrs-inbound: missing from or to field");
            return new WebhookResult.Ignored();
        }

        var meta = messageId != null
                ? Map.of("message-id", messageId, "event", "sms.received")
                : Map.of("event", (String) "sms.received");

        return new WebhookResult.Delivered(List.of(
                new InboundMessage(id(), InboundConnectorTypes.SMS,
                        from, to, content, List.of(), Instant.now(), meta, tenancyId)));
    }

    private WebhookResult handleVoice(JsonNode payload, String tenancyId) {
        var from = payload.path("from").asText(null);
        var to = payload.path("to").asText(null);
        var transcription = payload.path("transcription").asText("");
        var callId = payload.path("callId").asText(null);
        var durationSeconds = payload.has("durationSeconds")
                ? String.valueOf(payload.path("durationSeconds").asInt()) : null;
        var recordingUrl = payload.path("recordingUrl").asText(null);

        if (from == null || to == null) {
            LOG.warning("numbrrs-inbound: voice event missing from or to");
            return new WebhookResult.Ignored();
        }

        var metaBuilder = new java.util.LinkedHashMap<String, String>();
        metaBuilder.put("event", "voice.received");
        if (callId != null) metaBuilder.put("call-id", callId);
        if (durationSeconds != null) metaBuilder.put("duration-seconds", durationSeconds);
        if (recordingUrl != null) metaBuilder.put("recording-url", recordingUrl);

        var content = transcription.isBlank()
                ? "Voice call from " + from + " (no transcription)"
                : transcription;

        return new WebhookResult.Delivered(List.of(
                new InboundMessage(id(), InboundConnectorTypes.VOICE,
                        from, to, content, List.of(), Instant.now(), metaBuilder, tenancyId)));
    }

    boolean verifySignature(WebhookRequest request) {
        var sigHeader = request.header("x-numbrrs-signature");
        var tsHeader = request.header("x-numbrrs-timestamp");

        if (sigHeader == null || tsHeader == null) return false;

        long timestamp;
        try {
            timestamp = Long.parseLong(tsHeader);
        } catch (NumberFormatException e) {
            return false;
        }

        if (Math.abs(Instant.now().getEpochSecond() - timestamp) > MAX_AGE_SECONDS) {
            LOG.warning("numbrrs-inbound: webhook timestamp too old — possible replay");
            return false;
        }

        var signedPayload = tsHeader + "." + request.body();
        var expected = hmacSha256(signingSecret, signedPayload);
        var provided = sigHeader.startsWith("sha256=") ? sigHeader.substring(7) : sigHeader;

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    static String hmacSha256(String secret, String data) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 computation failed", e);
        }
    }
}
