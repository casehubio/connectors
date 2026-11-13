package io.casehub.connectors.it;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.casehub.connectors.HttpMethod;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;
import io.casehub.connectors.mobile.twilio.TwilioWhatsAppBridge;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppMediaFlowTest {

    private static final String SECRET = "integration-test-secret";
    private static final String VERIFY_TOKEN = "test-verify";

    @Test
    void textOnlyMessageReturnsDelivered() {
        var connector = new TwilioWhatsAppBridge(SECRET, VERIFY_TOKEN, "");
        var body = textMessage("15551234", "15559999", "Hello integration test");
        var request = signedPost(body);

        var result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        assertThat(delivered.messages()).hasSize(1);
        assertThat(delivered.messages().get(0).content()).isEqualTo("Hello integration test");
        assertThat(delivered.messages().get(0).externalSenderId()).isEqualTo("15551234");
        assertThat(delivered.messages().get(0).externalChannelRef()).isEqualTo("15559999");
    }

    @Test
    void audioMessageReturnsDeferredDeliveryWithEmptyImmediate() {
        var connector = new TwilioWhatsAppBridge(SECRET, VERIFY_TOKEN, "test-api-token");
        var body = mediaMessage("15551234", "audio", "media-audio-001", "audio/ogg");
        var request = signedPost(body);

        var result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.DeferredDelivery.class);
        var deferred = (WebhookResult.DeferredDelivery) result;
        assertThat(deferred.immediate()).isEmpty();
    }

    @Test
    void mixedTextAndMediaSplitsCorrectly() {
        var connector = new TwilioWhatsAppBridge(SECRET, VERIFY_TOKEN, "test-api-token");
        var body = mixedMessage("15551234", "Hello text", "media-img-001", "image/jpeg");
        var request = signedPost(body);

        var result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.DeferredDelivery.class);
        var deferred = (WebhookResult.DeferredDelivery) result;
        assertThat(deferred.immediate()).hasSize(1);
        assertThat(deferred.immediate().get(0).content()).isEqualTo("Hello text");
    }

    @Test
    void locationMessagePreservedAsImmediateWithMetadata() {
        var connector = new TwilioWhatsAppBridge(SECRET, VERIFY_TOKEN, "");
        var body = locationMessage("15551234");
        var request = signedPost(body);

        var result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        assertThat(delivered.messages()).hasSize(1);
        assertThat(delivered.messages().get(0).content()).isEmpty();
        assertThat(delivered.messages().get(0).metadata()).containsEntry("message-type", "location");
    }

    @Test
    void invalidSignatureReturnsUnauthorized() {
        var connector = new TwilioWhatsAppBridge(SECRET, VERIFY_TOKEN, "");
        var body = textMessage("15551234", "15559999", "test");
        var request = new WebhookRequest(body,
                Map.of("x-hub-signature-256", List.of("sha256=invalid")),
                Map.of(), HttpMethod.POST, "https://test.example.com/webhook");

        assertThat(connector.handle(request)).isInstanceOf(WebhookResult.Unauthorized.class);
    }

    @Test
    void getChallengeWithCorrectTokenReturnsChallenged() {
        var connector = new TwilioWhatsAppBridge(SECRET, VERIFY_TOKEN, "");
        var request = new WebhookRequest("",
                Map.of(), Map.of("hub.mode", "subscribe", "hub.verify_token", VERIFY_TOKEN,
                        "hub.challenge", "challenge-value"),
                HttpMethod.GET, "https://test.example.com/webhook");

        var result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Challenged.class);
        assertThat(((WebhookResult.Challenged) result).responseBody()).isEqualTo("challenge-value");
    }

    @Test
    void deferredSupplierDeliversFailureMetadataWhenDownloadFails() {
        var connector = new TwilioWhatsAppBridge(SECRET, VERIFY_TOKEN, "bad-token");
        var body = mediaMessage("15551234", "audio", "media-999", "audio/ogg");
        var request = signedPost(body);

        var result = connector.handle(request);
        assertThat(result).isInstanceOf(WebhookResult.DeferredDelivery.class);
        var deferred = (WebhookResult.DeferredDelivery) result;

        var messages = deferred.deferred().get();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).metadata()).containsEntry("download-failed", "true");
        assertThat(messages.get(0).metadata()).containsEntry("media-type", "audio");
    }

    private static WebhookRequest signedPost(String body) {
        byte[] hmac = hmacSha256(SECRET.getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8));
        String sig = "sha256=" + HexFormat.of().formatHex(hmac);
        return new WebhookRequest(body,
                Map.of("x-hub-signature-256", List.of(sig)),
                Map.of(), HttpMethod.POST, "https://test.example.com/webhook");
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String textMessage(String from, String phoneId, String text) {
        return "{\"entry\":[{\"changes\":[{\"value\":{\"messages\":[{\"id\":\"wamid.IT001\","
                + "\"from\":\"" + from + "\",\"type\":\"text\","
                + "\"text\":{\"body\":\"" + text + "\"}}],"
                + "\"metadata\":{\"phone_number_id\":\"" + phoneId + "\"}}}]}]}";
    }

    private static String mediaMessage(String from, String type, String mediaId, String mimeType) {
        return "{\"entry\":[{\"changes\":[{\"value\":{\"messages\":[{\"id\":\"wamid.IT002\","
                + "\"from\":\"" + from + "\",\"type\":\"" + type + "\","
                + "\"" + type + "\":{\"id\":\"" + mediaId + "\",\"mime_type\":\"" + mimeType + "\"}}],"
                + "\"metadata\":{\"phone_number_id\":\"15559999\"}}}]}]}";
    }

    private static String mixedMessage(String from, String text, String mediaId, String mimeType) {
        return "{\"entry\":[{\"changes\":[{\"value\":{\"messages\":["
                + "{\"id\":\"wamid.IT003\",\"from\":\"" + from + "\",\"type\":\"text\","
                + "\"text\":{\"body\":\"" + text + "\"}},"
                + "{\"id\":\"wamid.IT004\",\"from\":\"" + from + "\",\"type\":\"image\","
                + "\"image\":{\"id\":\"" + mediaId + "\",\"mime_type\":\"" + mimeType + "\"}}"
                + "],\"metadata\":{\"phone_number_id\":\"15559999\"}}}]}]}";
    }

    private static String locationMessage(String from) {
        return "{\"entry\":[{\"changes\":[{\"value\":{\"messages\":[{\"id\":\"wamid.IT005\","
                + "\"from\":\"" + from + "\",\"type\":\"location\","
                + "\"location\":{\"latitude\":51.5074,\"longitude\":-0.1278}}],"
                + "\"metadata\":{\"phone_number_id\":\"15559999\"}}}]}]}";
    }
}
