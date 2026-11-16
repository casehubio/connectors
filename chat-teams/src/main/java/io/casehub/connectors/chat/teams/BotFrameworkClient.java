package io.casehub.connectors.chat.teams;

import io.casehub.connectors.http.HttpHelper;
import io.casehub.platform.api.authn.ServiceConnectionProvider;
import jakarta.json.Json;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

class BotFrameworkClient {

    private static final Logger LOG = Logger.getLogger(BotFrameworkClient.class.getName());

    private final ServiceConnectionProvider connectionProvider;
    private final List<String> allowedHostPatterns;

    BotFrameworkClient(ServiceConnectionProvider connectionProvider, String serviceUrlAllowlist) {
        this.connectionProvider = connectionProvider;
        this.allowedHostPatterns = List.of(serviceUrlAllowlist.split(","));
    }

    void replyToActivity(String serviceUrl, String conversationId,
                         String activityId, String text) {
        validateServiceUrl(serviceUrl);
        String url = stripTrailingSlash(serviceUrl)
                + "/v3/conversations/" + conversationId + "/activities/" + activityId;
        String body = Json.createObjectBuilder()
                .add("type", "message")
                .add("text", text)
                .build().toString();
        postJson(url, body);
    }

    void sendToConversation(String serviceUrl, String conversationId, String text) {
        validateServiceUrl(serviceUrl);
        String url = stripTrailingSlash(serviceUrl)
                + "/v3/conversations/" + conversationId + "/activities";
        String body = Json.createObjectBuilder()
                .add("type", "message")
                .add("text", text)
                .build().toString();
        postJson(url, body);
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
            URI    uri   = URI.create(contentUrl);
            String token = resolveToken();
            var request = HttpRequest.newBuilder()
                                     .uri(uri)
                                     .header("Authorization", "Bearer " + token)
                                     .GET()
                                     .build();
            var response = HttpHelper.CLIENT.send(request,
                                                  HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                LOG.warning("teams-bot: attachment download failed: " + response.statusCode()
                            + " " + contentUrl);
                return null;
            }
            return new io.casehub.connectors.Attachment(name, contentType, response.body());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "teams-bot: attachment download failed: " + contentUrl, e);
            return null;
        }
    }


    void validateServiceUrl(String serviceUrl) {
        try {
            URI uri = URI.create(serviceUrl);
            String host = uri.getHost();
            if (host == null) {
                throw new IllegalArgumentException(
                        "teams-bot: no host in serviceUrl: " + serviceUrl);
            }
            for (String pattern : allowedHostPatterns) {
                String p = pattern.trim();
                if (p.startsWith("*.")) {
                    String suffix = p.substring(1);
                    if (host.endsWith(suffix) || host.equals(p.substring(2))) return;
                } else {
                    if (host.equals(p)) return;
                }
            }
            throw new IllegalArgumentException(
                    "teams-bot: serviceUrl host not in allowlist: " + host);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "teams-bot: invalid serviceUrl: " + serviceUrl, e);
        }
    }

    private void postJson(String url, String body) {
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
                LOG.warning("teams-bot: outbound call failed: " + response.statusCode()
                        + " " + url);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "teams-bot: outbound call failed: " + url, e);
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
                throw new RuntimeException("teams-bot: outbound call failed: "
                                           + response.statusCode() + " " + url + " body: " + response.body());
            }
            return response.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("teams-bot: outbound call failed: " + url, e);
        }
    }


    private String resolveToken() {
        if (connectionProvider == null) return "test-token";
        return connectionProvider.getAccessToken(TeamsChatPlatform.APPLICATION_ACTOR,
                "microsoft-bot", "default").accessToken();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
