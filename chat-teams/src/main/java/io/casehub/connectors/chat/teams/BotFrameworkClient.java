package io.casehub.connectors.chat.teams;

import io.casehub.connectors.http.HttpHelper;
import io.casehub.platform.api.authn.ServiceConnectionProvider;
import jakarta.json.Json;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

class BotFrameworkClient {

    private static final Logger LOG = Logger.getLogger(BotFrameworkClient.class.getName());
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    static final long DEFAULT_MAX_ATTACHMENT_BYTES = 8 * 1024 * 1024;

    private final ServiceConnectionProvider connectionProvider;
    private final List<String> allowedHostPatterns;
    private final List<String> allowedAttachmentHostPatterns;
    private final long maxAttachmentBytes;


    BotFrameworkClient(ServiceConnectionProvider connectionProvider, String serviceUrlAllowlist) {
        this(connectionProvider, serviceUrlAllowlist, serviceUrlAllowlist, DEFAULT_MAX_ATTACHMENT_BYTES);
    }

    BotFrameworkClient(ServiceConnectionProvider connectionProvider,
                       String serviceUrlAllowlist, String attachmentUrlAllowlist) {
        this(connectionProvider, serviceUrlAllowlist, attachmentUrlAllowlist, DEFAULT_MAX_ATTACHMENT_BYTES);
    }

    BotFrameworkClient(ServiceConnectionProvider connectionProvider,
                       String serviceUrlAllowlist, String attachmentUrlAllowlist,
                       long maxAttachmentBytes) {
        this.connectionProvider            = connectionProvider;
        this.allowedHostPatterns           = List.of(serviceUrlAllowlist.split(","));
        this.allowedAttachmentHostPatterns = List.of(attachmentUrlAllowlist.split(","));
        this.maxAttachmentBytes            = maxAttachmentBytes;
    }

    String replyToActivity(String serviceUrl, String conversationId,
                           String activityId, String text) {
        validateServiceUrl(serviceUrl);
        String url = stripTrailingSlash(serviceUrl)
                     + "/v3/conversations/" + conversationId + "/activities/" + activityId;
        String body = Json.createObjectBuilder()
                          .add("type", "message")
                          .add("text", text)
                          .build().toString();
        String responseBody = postJsonWithResponse(url, body);
        try (var reader = Json.createReader(new StringReader(responseBody))) {
            return reader.readObject().getString("id", "");
        }
    }

    String sendToConversation(String serviceUrl, String conversationId, String text) {
        validateServiceUrl(serviceUrl);
        String url = stripTrailingSlash(serviceUrl)
                     + "/v3/conversations/" + conversationId + "/activities";
        String body = Json.createObjectBuilder()
                          .add("type", "message")
                          .add("text", text)
                          .build().toString();
        String responseBody = postJsonWithResponse(url, body);
        try (var reader = Json.createReader(new StringReader(responseBody))) {
            return reader.readObject().getString("id", "");
        }
    }

    String createConversation(String serviceUrl, String botAppId,
                              String aadObjectId, String tenantId) {
        validateServiceUrl(serviceUrl);
        String url = stripTrailingSlash(serviceUrl) + "/v3/conversations";
        String body = Json.createObjectBuilder()
                          .add("bot", Json.createObjectBuilder().add("id", "28:" + botAppId))
                          .add("members", Json.createArrayBuilder()
                                              .add(Json.createObjectBuilder().add("id", aadObjectId)))
                          .add("tenantId", tenantId)
                          .add("isGroup", false)
                          .build().toString();
        String responseBody = postJsonWithResponse(url, body);
        try (var reader = Json.createReader(new StringReader(responseBody))) {
            return reader.readObject().getString("id");
        }
    }


    io.casehub.connectors.Attachment downloadAttachment(String contentUrl, String contentType, String name) {
        try {
            URI    uri  = URI.create(contentUrl);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                LOG.warning("teams-bot: attachment URL rejected (non-HTTPS) — " + contentUrl);
                return null;
            }
            if (host == null || !matchesHostPatterns(host, allowedAttachmentHostPatterns)) {
                LOG.warning("teams-bot: attachment URL host rejected (SSRF defense) — " + contentUrl);
                return null;
            }
            String token = resolveToken();
            var request = HttpRequest.newBuilder()
                                     .uri(uri)
                                     .header("Authorization", "Bearer " + token)
                                     .timeout(REQUEST_TIMEOUT)
                                     .GET()
                                     .build();
            var response = HttpHelper.CLIENT.send(request,
                                                  HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    LOG.warning("teams-bot: attachment download failed: " + response.statusCode()
                                + " " + contentUrl);
                    return null;
                }
                long contentLength = response.headers()
                        .firstValueAsLong("Content-Length").orElse(-1);
                if (contentLength > maxAttachmentBytes) {
                    LOG.warning(String.format(
                            "teams-bot: attachment too large (%d bytes, limit %d) — %s",
                            contentLength, maxAttachmentBytes, name));
                    return null;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                long total = 0;
                int read;
                while ((read = in.read(buf)) != -1) {
                    total += read;
                    if (total > maxAttachmentBytes) {
                        LOG.warning(String.format(
                                "teams-bot: attachment stream exceeded limit (%d bytes) — %s",
                                maxAttachmentBytes, name));
                        return null;
                    }
                    out.write(buf, 0, read);
                }
                return new io.casehub.connectors.Attachment(name, contentType, out.toByteArray());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "teams-bot: attachment download failed: " + contentUrl, e);
            return null;
        }
    }


    void validateServiceUrl(String serviceUrl) {
        try {
            URI    uri  = URI.create(serviceUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException(
                        "teams-bot: non-HTTPS serviceUrl rejected: " + serviceUrl);
            }
            String host = uri.getHost();
            if (host == null) {
                throw new IllegalArgumentException(
                        "teams-bot: no host in serviceUrl: " + serviceUrl);
            }
            if (!matchesHostPatterns(host, allowedHostPatterns)) {
                throw new IllegalArgumentException(
                        "teams-bot: serviceUrl host not in allowlist: " + host);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "teams-bot: invalid serviceUrl: " + serviceUrl, e);
        }
    }

    String postJsonWithResponse(String url, String body) {
        try {
            String token = resolveToken();
            var request = HttpRequest.newBuilder()
                                     .uri(URI.create(url))
                                     .header("Content-Type", "application/json")
                                     .header("Authorization", "Bearer " + token)
                                     .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                                     .build();
            var response = HttpHelper.CLIENT.send(request,
                                                  HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw checkResponse(response.statusCode(), url, response.body());
            }
            return response.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("teams-bot: outbound call failed: " + url, e);
        }
    }

    static RuntimeException checkResponse(int statusCode, String url, String body) {
        return io.casehub.connectors.ProviderErrorMapping.mapStatusCode(
                statusCode, body, "microsoft-bot", null);
    }


    private String resolveToken() {
        if (connectionProvider == null) return "test-token";
        return connectionProvider.getAccessToken(TeamsChatPlatform.APPLICATION_ACTOR,
                "microsoft-bot", "default").accessToken();
    }


    static boolean matchesHostPatterns(String host, List<String> patterns) {
        for (String pattern : patterns) {
            String p = pattern.trim();
            if (p.startsWith("*.")) {
                String suffix = p.substring(1);
                if (host.endsWith(suffix) || host.equals(p.substring(2))) {return true;}
            } else {
                if (host.equals(p)) {return true;}
            }
        }
        return false;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
