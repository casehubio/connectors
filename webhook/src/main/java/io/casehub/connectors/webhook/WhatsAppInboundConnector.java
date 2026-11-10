package io.casehub.connectors.webhook;

import io.casehub.connectors.Attachment;
import io.casehub.connectors.HttpMethod;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Inbound connector for WhatsApp Business API (Meta Cloud API webhooks).
 *
 * <h2>GET challenge (subscription verification)</h2>
 * Meta sends a GET with {@code hub.mode=subscribe}, {@code hub.verify_token}, and
 * {@code hub.challenge}. If the verify token matches, respond with the challenge value
 * ({@code text/plain}). Wrong token → {@link WebhookResult.Unauthorized} (mapped to
 * HTTP 403 by the router so the admin console shows a clear failure).
 *
 * <h2>POST signature</h2>
 * HMAC-SHA256 of the raw body using the app secret. Compared to
 * {@code X-Hub-Signature-256: sha256=<hex>} using constant-time comparison.
 *
 * <h2>Media messages</h2>
 * Text messages are delivered immediately. Media messages (image, audio, document, video,
 * sticker) return {@link WebhookResult.DeferredDelivery} — the router delivers text messages
 * synchronously and spawns a virtual thread to download media via the Meta Graph API
 * (3-attempt exponential backoff). Other message types (contacts, location, etc.) are
 * delivered immediately with empty content and a {@code message-type} metadata entry.
 */
public class WhatsAppInboundConnector extends WebhookInboundConnector {

    public static final String ID = io.casehub.connectors.InboundConnectorIds.WHATSAPP;

    private static final Logger LOG = Logger.getLogger(WhatsAppInboundConnector.class.getName());

    private final String appSecret;
    private final String verifyToken;
    private final String apiToken;

    public WhatsAppInboundConnector(final String appSecret, final String verifyToken,
                                     final String apiToken) {
        this.appSecret = appSecret;
        this.verifyToken = verifyToken;
        this.apiToken = apiToken;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public WebhookResult handle(final WebhookRequest request) {
        try {
            return doHandle(request);
        } catch (final Exception e) {
            LOG.severe("Unexpected error in WhatsAppInboundConnector: " + e.getMessage());
            return new WebhookResult.Ignored();
        }
    }

    private WebhookResult doHandle(final WebhookRequest request) {
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

        final List<InboundMessage> textMessages = new ArrayList<>();
        final List<MediaRef>       mediaRefs    = new ArrayList<>();
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

    private WebhookResult handleGetChallenge(final WebhookRequest request) {
        final String mode = request.queryParams().get("hub.mode");
        final String token = request.queryParams().get("hub.verify_token");
        final String challenge = request.queryParams().get("hub.challenge");

        // Constant-time token comparison — consistent with the security model for HMAC paths.
        // Note: MessageDigest.isEqual leaks the length of verifyToken, which is acceptable
        // given this is a human-initiated admin action (not an automated polling loop).
        final byte[] expected = verifyToken.getBytes(StandardCharsets.UTF_8);
        final byte[] actual = (token == null ? "" : token).getBytes(StandardCharsets.UTF_8);

        if ("subscribe".equals(mode) && SigHelper.constantTimeEquals(expected, actual)
                && challenge != null) {
            return new WebhookResult.Challenged(challenge, "text/plain");
        }
        return new WebhookResult.Unauthorized();
    }

    private boolean verifySignature(final String body, final String sigHeader) {
        if (sigHeader == null || !sigHeader.startsWith("sha256=")) return false;
        final byte[] hmac = SigHelper.hmac("HmacSHA256",
                appSecret.getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8));
        final byte[] expected = ("sha256=" + SigHelper.toHex(hmac))
                .getBytes(StandardCharsets.UTF_8);
        return SigHelper.constantTimeEquals(expected, sigHeader.getBytes(StandardCharsets.UTF_8));
    }

    private record MediaRef(String from, String phoneNumberId, String type,
                             String mediaId, String mimeType, String msgId) {}

    private void parseAndSplit(final String body, final String tenancyId,
                               final List<InboundMessage> textOut, final List<MediaRef> mediaOut) {
        try {
            final JsonObject root = Json.createReader(new StringReader(body)).readObject();
            final JsonArray entries = root.getJsonArray("entry");
            if (entries == null) return;

            for (final var entry : entries) {
                final JsonArray changes = ((JsonObject) entry).getJsonArray("changes");
                if (changes == null) continue;
                for (final var change : changes) {
                    final JsonObject value = ((JsonObject) change).getJsonObject("value");
                    if (value == null) continue;
                    splitMessages(value, tenancyId, textOut, mediaOut);
                }
            }
        } catch (final Exception e) {
            LOG.warning("whatsapp-inbound: failed to parse event body: " + e.getMessage());
        }
    }

    private static final List<String> MEDIA_TYPES = List.of("image", "audio", "document", "video", "sticker");

    private void splitMessages(final JsonObject value, final String tenancyId,
                               final List<InboundMessage> textOut, final List<MediaRef> mediaOut) {
        final JsonArray msgs = value.getJsonArray("messages");
        if (msgs == null) return;
        final JsonObject metadata = value.getJsonObject("metadata");
        final String phoneNumberId = (metadata != null)
                ? metadata.getString("phone_number_id", "unknown") : "unknown";

        for (final var m : msgs) {
            final JsonObject msg = (JsonObject) m;
            final String from = msg.getString("from", "unknown");
            final String type = msg.getString("type", "text");
            final String msgId = msg.getString("id", null);

            if ("text".equals(type)) {
                final JsonObject text = msg.getJsonObject("text");
                final String content = (text != null) ? text.getString("body", "") : "";
                final Map<String, String> meta = msgId != null
                        ? Map.of("message-id", msgId) : Map.of();
                textOut.add(new InboundMessage(ID, InboundConnectorTypes.WHATSAPP,
                        from, phoneNumberId, content, List.of(), Instant.now(), meta, tenancyId));
            } else if (MEDIA_TYPES.contains(type)) {
                final JsonObject media = msg.getJsonObject(type);
                final String mediaId = (media != null) ? media.getString("id", null) : null;
                final String mimeType = (media != null) ? media.getString("mime_type", null) : null;
                if (mediaId != null) {
                    mediaOut.add(new MediaRef(from, phoneNumberId, type, mediaId, mimeType, msgId));
                }
            } else {
                final Map<String, String> meta = msgId != null
                        ? Map.of("message-id", msgId, "message-type", type)
                        : Map.of("message-type", type);
                textOut.add(new InboundMessage(ID, InboundConnectorTypes.WHATSAPP,
                        from, phoneNumberId, "", List.of(), Instant.now(), meta, tenancyId));
            }
        }
    }

    private List<InboundMessage> downloadMediaMessages(final List<MediaRef> refs,
                                                       final String tenancyId) {
        final List<InboundMessage> results = new ArrayList<>();
        for (final MediaRef ref : refs) {
            results.add(downloadSingleMedia(ref, tenancyId));
        }
        return results;
    }

    private InboundMessage downloadSingleMedia(final MediaRef ref, final String tenancyId) {
        byte[] mediaBytes = null;
        int attempts = 3;
        int delayMs = 1000;

        for (int i = 0; i < attempts; i++) {
            try {
                final String downloadUrl = fetchMediaUrl(ref.mediaId());
                mediaBytes = downloadBinary(downloadUrl);
                break;
            } catch (final Exception e) {
                LOG.warning("whatsapp-inbound: media download attempt " + (i + 1)
                        + "/" + attempts + " failed for " + ref.mediaId() + ": " + e.getMessage());
                if (i < attempts - 1) {
                    try { Thread.sleep(delayMs); } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    delayMs *= 2;
                }
            }
        }

        final Map<String, String> meta = new HashMap<>();
        if (ref.msgId() != null) meta.put("message-id", ref.msgId());
        meta.put("media-type", ref.type());

        if (mediaBytes == null) {
            meta.put("download-failed", "true");
            meta.put("media-id", ref.mediaId());
            return new InboundMessage(ID, InboundConnectorTypes.WHATSAPP,
                    ref.from(), ref.phoneNumberId(), "", List.of(),
                    Instant.now(), meta, tenancyId);
        }

        final String ext = extensionForMimeType(ref.mimeType(), ref.type());
        final Attachment attachment = new Attachment(
                ref.type() + "-" + ref.mediaId() + ext, ref.mimeType(), mediaBytes);
        return new InboundMessage(ID, InboundConnectorTypes.WHATSAPP,
                ref.from(), ref.phoneNumberId(), "", List.of(attachment),
                Instant.now(), meta, tenancyId);
    }

    private String fetchMediaUrl(final String mediaId) throws Exception {
        final var req = HttpRequest.newBuilder()
                .uri(URI.create("https://graph.facebook.com/v18.0/" + mediaId))
                .header("Authorization", "Bearer " + apiToken)
                .GET().build();
        final var resp = HttpHelper.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Media URL fetch failed: " + resp.statusCode());
        }
        final var json = Json.createReader(new StringReader(resp.body())).readObject();
        return json.getString("url");
    }

    private byte[] downloadBinary(final String url) throws Exception {
        final var uri = URI.create(url);
        if (!"https".equals(uri.getScheme())) {
            throw new SecurityException("whatsapp-inbound: refusing non-HTTPS media URL: " + uri.getScheme());
        }
        final String host = uri.getHost();
        if (host == null || host.startsWith("10.") || host.startsWith("172.")
                || host.startsWith("192.168.") || host.startsWith("169.254.")
                || "localhost".equals(host) || host.endsWith(".local")) {
            throw new SecurityException("whatsapp-inbound: refusing private/local media URL: " + host);
        }
        final var req = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + apiToken)
                .GET().build();
        final var resp = HttpHelper.CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Media download failed: " + resp.statusCode());
        }
        return resp.body();
    }

    private static String extensionForMimeType(final String mimeType, final String type) {
        if (mimeType != null && mimeType.contains("/")) {
            final String sub = mimeType.substring(mimeType.indexOf('/') + 1).split(";")[0].trim();
            return "." + sub;
        }
        return switch (type) {
            case "audio" -> ".ogg";
            case "image" -> ".jpg";
            case "video" -> ".mp4";
            default -> ".bin";
        };
    }
}
