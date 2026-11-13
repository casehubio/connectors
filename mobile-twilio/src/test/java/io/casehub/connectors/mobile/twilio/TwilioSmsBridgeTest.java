package io.casehub.connectors.mobile.twilio;

import io.casehub.connectors.HttpMethod;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

class TwilioSmsBridgeTest {

    private static final String AUTH_TOKEN = "test-twilio-auth-token";

    @Test
    void idReturnsTwilioSmsInbound() {
        var bridge = new TwilioSmsBridge(AUTH_TOKEN);
        assertThat(bridge.id()).isEqualTo(InboundConnectorIds.TWILIO_SMS);
    }

    @Test
    void parseFormBodyParsesValidBody() {
        var result = TwilioSmsBridge.parseFormBody("From=%2B441234&To=%2B449876&Body=hello+world");
        assertThat(result).containsEntry("From", "+441234");
        assertThat(result).containsEntry("To", "+449876");
        assertThat(result).containsEntry("Body", "hello world");
    }

    @Test
    void parseFormBodyHandlesNull() {
        assertThat(TwilioSmsBridge.parseFormBody(null)).isEmpty();
    }

    @Test
    void parseFormBodyHandlesEmpty() {
        assertThat(TwilioSmsBridge.parseFormBody("")).isEmpty();
    }

    @Test
    void validSignatureAccepted() {
        var bridge = new TwilioSmsBridge(AUTH_TOKEN);
        var params = new TreeMap<String, String>();
        params.put("From", "+447700900001");
        params.put("To", "+447700900002");
        params.put("Body", "test sms");
        params.put("MessageSid", "SM999");

        var url = "https://example.com/connectors/twilio-sms-inbound/webhook";
        var sig = computeTwilioSignature(url, params, AUTH_TOKEN);
        var formBody = buildFormBody(params);

        var request = new WebhookRequest(formBody,
                Map.of("x-twilio-signature", List.of(sig)),
                Map.of(), HttpMethod.POST, url);

        var result = bridge.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        assertThat(delivered.messages()).hasSize(1);
        assertThat(delivered.messages().get(0).externalSenderId()).isEqualTo("+447700900001");
        assertThat(delivered.messages().get(0).content()).isEqualTo("test sms");
    }

    @Test
    void invalidSignatureRejected() {
        var bridge = new TwilioSmsBridge(AUTH_TOKEN);
        var formBody = "From=%2B441234&To=%2B449876&Body=test";

        var request = new WebhookRequest(formBody,
                Map.of("x-twilio-signature", List.of("invalid-signature")),
                Map.of(), HttpMethod.POST, "https://example.com/webhook");

        var result = bridge.handle(request);
        assertThat(result).isInstanceOf(WebhookResult.Unauthorized.class);
    }

    @Test
    void missingSignatureRejected() {
        var bridge = new TwilioSmsBridge(AUTH_TOKEN);
        var formBody = "From=%2B441234&To=%2B449876&Body=test";

        var request = new WebhookRequest(formBody,
                Map.of(), Map.of(), HttpMethod.POST, "https://example.com/webhook");

        var result = bridge.handle(request);
        assertThat(result).isInstanceOf(WebhookResult.Unauthorized.class);
    }

    @Test
    void blankAuthTokenIgnoresRequest() {
        var bridge = new TwilioSmsBridge("");
        var request = new WebhookRequest("From=%2B441234&To=%2B449876&Body=test",
                Map.of(), Map.of(), HttpMethod.POST, "https://example.com/webhook");

        var result = bridge.handle(request);
        assertThat(result).isInstanceOf(WebhookResult.Ignored.class);
    }

    private static String computeTwilioSignature(String url, TreeMap<String, String> params, String token) {
        var toSign = new StringBuilder(url);
        params.forEach((k, v) -> toSign.append(k).append(v));
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(toSign.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String buildFormBody(TreeMap<String, String> params) {
        var sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append("&");
            sb.append(java.net.URLEncoder.encode(k, StandardCharsets.UTF_8))
              .append("=")
              .append(java.net.URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }
}
