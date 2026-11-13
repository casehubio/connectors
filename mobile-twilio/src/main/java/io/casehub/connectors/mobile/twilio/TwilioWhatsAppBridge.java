package io.casehub.connectors.mobile.twilio;

import io.casehub.connectors.Attachment;
import io.casehub.connectors.HttpMethod;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.WebhookInboundConnector;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;
import io.casehub.connectors.http.HttpHelper;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class TwilioWhatsAppBridge extends WebhookInboundConnector {

    private static final Logger LOG = Logger.getLogger(TwilioWhatsAppBridge.class.getName());
    private static final List<String> MEDIA_TYPES = List.of("image", "audio", "document", "video", "sticker");

    private final String appSecret;
    private final String verifyToken;
    private final String apiToken;

    public TwilioWhatsAppBridge(String appSecret, String verifyToken, String apiToken) {
        this.appSecret = appSecret;
        this.verifyToken = verifyToken;
        this.apiToken = apiToken;
    }

    @Override
    public String id() {
        return InboundConnectorIds.WHATSAPP;
    }

    @Override
    public WebhookResult handle(WebhookRequest request) {
        try {
            return doHandle(request);
        } catch (Exception e) {
            LOG.severe("Unexpected error in TwilioWhatsAppBridge: " + e.getMessage());
            return new WebhookResult.Ignored();
        }
    }

    private WebhookResult doHandle(WebhookRequest request) {
        if (request.method() == HttpMethod.GET) {
            return handleGetChallenge(request);
        }

        if (appSecret.isBlank()) {
            LOG.warning("whatsapp-inbound: app-secret not configured — ignoring request");
            return new WebhookResult.Ignored();
        }

        if (!verifySignature(request.body(), request.header("x-hub-signature-256"))) {
            return new WebhookResult.Unauthorized();
        }

        var textMessages = new ArrayList<InboundMessage>();
        var mediaRefs = new ArrayList<MediaRef>();
        parseAndSplit(request.body(), request.tenancyId(), textMessages, mediaRefs);

        if (textMessages.isEmpty() && mediaRefs.isEmpty()) {
            return new WebhookResult.Ignored();
        }
        if (mediaRefs.isEmpty()) {
            return new WebhookResult.Delivered(textMessages);
        }
        return new WebhookResult.DeferredDelivery(textMessages,
                () -> downloadMediaMessages(mediaRefs, request.tenancyId()));
    }

    private WebhookResult handleGetChallenge(WebhookRequest request) {
        var mode = request.queryParams().get("hub.mode");
        var token = request.queryParams().get("hub.verify_token");
        var challenge = request.queryParams().get("hub.challenge");

        var expected = verifyToken.getBytes(StandardCharsets.UTF_8);
        var actual = (token == null ? "" : token).getBytes(StandardCharsets.UTF_8);

        if ("subscribe".equals(mode) && MessageDigest.isEqual(expected, actual)
                && challenge != null) {
            return new WebhookResult.Challenged(challenge, "text/plain");
        }
        return new WebhookResult.Unauthorized();
    }

    private boolean verifySignature(String body, String sigHeader) {
        if (sigHeader == null || !sigHeader.startsWith("sha256=")) return false;
        var hmac = hmac("HmacSHA256",
                appSecret.getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8));
        var expected = ("sha256=" + HexFormat.of().formatHex(hmac))
                .getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, sigHeader.getBytes(StandardCharsets.UTF_8));
    }

    private record MediaRef(String from, String phoneNumberId, String type,
                             String mediaId, String mimeType, String msgId) {}

    private void parseAndSplit(String body, String tenancyId,
                               List<InboundMessage> textOut, List<MediaRef> mediaOut) {
        try {
            var root = Json.createReader(new StringReader(body)).readObject();
            var entries = root.getJsonArray("entry");
            if (entries == null) return;

            for (var entry : entries) {
                var changes = ((JsonObject) entry).getJsonArray("changes");
                if (changes == null) continue;
                for (var change : changes) {
                    var value = ((JsonObject) change).getJsonObject("value");
                    if (value == null) continue;
                    splitMessages(value, tenancyId, textOut, mediaOut);
                }
            }
        } catch (Exception e) {
            LOG.warning("whatsapp-inbound: failed to parse event body: " + e.getMessage());
        }
    }

    private void splitMessages(JsonObject value, String tenancyId,
                               List<InboundMessage> textOut, List<MediaRef> mediaOut) {
        var msgs = value.getJsonArray("messages");
        if (msgs == null) return;
        var metadata = value.getJsonObject("metadata");
        var phoneNumberId = (metadata != null)
                ? metadata.getString("phone_number_id", "unknown") : "unknown";

        for (var m : msgs) {
            var msg = (JsonObject) m;
            var from = msg.getString("from", "unknown");
            var type = msg.getString("type", "text");
            var msgId = msg.getString("id", null);

            if ("text".equals(type)) {
                var text = msg.getJsonObject("text");
                var content = (text != null) ? text.getString("body", "") : "";
                var meta = msgId != null ? Map.of("message-id", msgId) : Map.<String, String>of();
                textOut.add(new InboundMessage(id(), InboundConnectorTypes.WHATSAPP,
                        from, phoneNumberId, content, List.of(), Instant.now(), meta, tenancyId));
            } else if (MEDIA_TYPES.contains(type)) {
                var media = msg.getJsonObject(type);
                var mediaId = (media != null) ? media.getString("id", null) : null;
                var mimeType = (media != null) ? media.getString("mime_type", null) : null;
                if (mediaId != null) {
                    mediaOut.add(new MediaRef(from, phoneNumberId, type, mediaId, mimeType, msgId));
                }
            } else {
                var meta = msgId != null
                        ? Map.of("message-id", msgId, "message-type", type)
                        : Map.of("message-type", type);
                textOut.add(new InboundMessage(id(), InboundConnectorTypes.WHATSAPP,
                        from, phoneNumberId, "", List.of(), Instant.now(), meta, tenancyId));
            }
        }
    }

    private List<InboundMessage> downloadMediaMessages(List<MediaRef> refs, String tenancyId) {
        var results = new ArrayList<InboundMessage>();
        for (var ref : refs) {
            results.add(downloadSingleMedia(ref, tenancyId));
        }
        return results;
    }

    private InboundMessage downloadSingleMedia(MediaRef ref, String tenancyId) {
        byte[] mediaBytes = null;
        int attempts = 3;
        int delayMs = 1000;

        for (int i = 0; i < attempts; i++) {
            try {
                var downloadUrl = fetchMediaUrl(ref.mediaId());
                mediaBytes = downloadBinary(downloadUrl);
                break;
            } catch (Exception e) {
                LOG.warning("whatsapp-inbound: media download attempt " + (i + 1)
                        + "/" + attempts + " failed for " + ref.mediaId() + ": " + e.getMessage());
                if (i < attempts - 1) {
                    try { Thread.sleep(delayMs); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    delayMs *= 2;
                }
            }
        }

        var meta = new HashMap<String, String>();
        if (ref.msgId() != null) meta.put("message-id", ref.msgId());
        meta.put("media-type", ref.type());

        if (mediaBytes == null) {
            meta.put("download-failed", "true");
            meta.put("media-id", ref.mediaId());
            return new InboundMessage(id(), InboundConnectorTypes.WHATSAPP,
                    ref.from(), ref.phoneNumberId(), "", List.of(),
                    Instant.now(), meta, tenancyId);
        }

        var ext = extensionForMimeType(ref.mimeType(), ref.type());
        var attachment = new Attachment(
                ref.type() + "-" + ref.mediaId() + ext, ref.mimeType(), mediaBytes);
        return new InboundMessage(id(), InboundConnectorTypes.WHATSAPP,
                ref.from(), ref.phoneNumberId(), "", List.of(attachment),
                Instant.now(), meta, tenancyId);
    }

    private String fetchMediaUrl(String mediaId) throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create("https://graph.facebook.com/v18.0/" + mediaId))
                .header("Authorization", "Bearer " + apiToken)
                .GET().build();
        var resp = HttpHelper.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Media URL fetch failed: " + resp.statusCode());
        }
        var json = Json.createReader(new StringReader(resp.body())).readObject();
        return json.getString("url");
    }

    private byte[] downloadBinary(String url) throws Exception {
        var uri = URI.create(url);
        if (!"https".equals(uri.getScheme())) {
            throw new SecurityException("whatsapp-inbound: refusing non-HTTPS media URL: " + uri.getScheme());
        }
        var host = uri.getHost();
        if (host == null || host.startsWith("10.") || host.startsWith("172.")
                || host.startsWith("192.168.") || host.startsWith("169.254.")
                || "localhost".equals(host) || host.endsWith(".local")) {
            throw new SecurityException("whatsapp-inbound: refusing private/local media URL: " + host);
        }
        var req = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + apiToken)
                .GET().build();
        var resp = HttpHelper.CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Media download failed: " + resp.statusCode());
        }
        return resp.body();
    }

    private static String extensionForMimeType(String mimeType, String type) {
        if (mimeType != null && mimeType.contains("/")) {
            var sub = mimeType.substring(mimeType.indexOf('/') + 1).split(";")[0].trim();
            return "." + sub;
        }
        return switch (type) {
            case "audio" -> ".ogg";
            case "image" -> ".jpg";
            case "video" -> ".mp4";
            default -> ".bin";
        };
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
