package io.casehub.connectors.mobile.twilio;

import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.WebhookInboundConnector;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class TwilioSmsBridge extends WebhookInboundConnector {

    private static final Logger LOG = Logger.getLogger(TwilioSmsBridge.class.getName());

    private final String authToken;

    public TwilioSmsBridge(String authToken) {
        this.authToken = authToken;
    }

    @Override
    public String id() {
        return InboundConnectorIds.TWILIO_SMS;
    }

    @Override
    public WebhookResult handle(WebhookRequest request) {
        try {
            return doHandle(request);
        } catch (Exception e) {
            LOG.severe("Unexpected error in TwilioSmsBridge: " + e.getMessage());
            return new WebhookResult.Ignored();
        }
    }

    private WebhookResult doHandle(WebhookRequest request) {
        if (authToken.isBlank()) {
            LOG.warning("twilio-sms-inbound: auth-token not configured — ignoring request");
            return new WebhookResult.Ignored();
        }

        var params = parseFormBody(request.body());

        if (!verifySignature(request.requestUrl(), params, request.header("x-twilio-signature"))) {
            return new WebhookResult.Unauthorized();
        }

        var from = params.get("From");
        var to = params.get("To");
        var body = params.getOrDefault("Body", "");

        if (from == null || to == null) {
            LOG.warning("twilio-sms-inbound: missing From or To field");
            return new WebhookResult.Ignored();
        }

        var messageSid = params.get("MessageSid");
        var meta = messageSid != null ? Map.of("message-sid", messageSid) : Map.<String, String>of();
        return new WebhookResult.Delivered(
                List.of(new InboundMessage(id(), InboundConnectorTypes.SMS,
                        from, to, body, List.of(), Instant.now(), meta, request.tenancyId())));
    }

    private boolean verifySignature(String url, Map<String, String> params, String sigHeader) {
        if (sigHeader == null || sigHeader.isBlank()) return false;
        var toSign = new StringBuilder(url);
        new TreeMap<>(params).forEach((k, v) -> toSign.append(k).append(v));

        var hmac = hmac("HmacSHA1",
                authToken.getBytes(StandardCharsets.UTF_8),
                toSign.toString().getBytes(StandardCharsets.UTF_8));
        var expected = Base64.getEncoder().encodeToString(hmac)
                .getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, sigHeader.trim().getBytes(StandardCharsets.UTF_8));
    }

    static Map<String, String> parseFormBody(String body) {
        var result = new LinkedHashMap<String, String>();
        if (body == null || body.isBlank()) return result;
        for (var pair : body.split("&")) {
            var eq = pair.indexOf('=');
            if (eq < 0) continue;
            var key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            var val = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.put(key, val);
        }
        return result;
    }

    private static byte[] hmac(String algorithm, byte[] keyBytes, byte[] dataBytes) {
        try {
            var mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(keyBytes, algorithm));
            return mac.doFinal(dataBytes);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed: " + algorithm, e);
        }
    }
}
