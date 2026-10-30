package io.casehub.connectors.email.google.inbound;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import io.casehub.connectors.http.HttpHelper;
import io.casehub.platform.api.authn.ServiceConnectionProvider;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

public class GmailHistoryClient {

    private static final Logger LOG = Logger.getLogger(GmailHistoryClient.class.getName());
    private static final String GMAIL_API = "https://gmail.googleapis.com/gmail/v1/users/me";

    private final ServiceConnectionProvider connectionProvider;

    public GmailHistoryClient(ServiceConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public List<String> getNewMessageIds(String emailAddress, long startHistoryId) {
        String token = connectionProvider.getAccessToken(emailAddress, "google", null).accessToken();
        String url = GMAIL_API + "/history?startHistoryId=" + startHistoryId
                + "&historyTypes=messageAdded";
        var req = HttpRequest.newBuilder().uri(URI.create(url))
                .header("Authorization", "Bearer " + token).GET().build();
        try {
            var resp = HttpHelper.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                LOG.warning("gmail-history: API returned " + resp.statusCode());
                return List.of();
            }
            return parseMessageIds(resp.body());
        } catch (Exception e) {
            LOG.warning("gmail-history: failed to fetch history: " + e.getMessage());
            return List.of();
        }
    }

    public long getCurrentHistoryId(String emailAddress) {
        String token = connectionProvider.getAccessToken(emailAddress, "google", null).accessToken();
        var req = HttpRequest.newBuilder()
                .uri(URI.create(GMAIL_API + "/profile"))
                .header("Authorization", "Bearer " + token).GET().build();
        try {
            var resp = HttpHelper.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            var json = Json.createReader(new StringReader(resp.body())).readObject();
            return json.getJsonNumber("historyId").longValue();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Gmail profile historyId", e);
        }
    }

    public MessageSummary getMessageSummary(String emailAddress, String messageId) {
        String token = connectionProvider.getAccessToken(emailAddress, "google", null).accessToken();
        String url = GMAIL_API + "/messages/" + messageId + "?format=metadata"
                + "&metadataHeaders=From&metadataHeaders=Subject";
        var req = HttpRequest.newBuilder().uri(URI.create(url))
                .header("Authorization", "Bearer " + token).GET().build();
        try {
            var resp = HttpHelper.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return new MessageSummary(messageId, "", "", "");
            }
            var json = Json.createReader(new StringReader(resp.body())).readObject();
            String snippet = json.getString("snippet", "");
            String from = "";
            String subject = "";
            JsonObject payload = json.getJsonObject("payload");
            if (payload != null) {
                JsonArray headers = payload.getJsonArray("headers");
                if (headers != null) {
                    for (var h : headers) {
                        var header = (JsonObject) h;
                        String name = header.getString("name", "");
                        if ("From".equalsIgnoreCase(name)) from = header.getString("value", "");
                        if ("Subject".equalsIgnoreCase(name)) subject = header.getString("value", "");
                    }
                }
            }
            return new MessageSummary(messageId, from, subject, snippet);
        } catch (Exception e) {
            LOG.warning("gmail-history: failed to fetch message " + messageId + ": " + e.getMessage());
            return new MessageSummary(messageId, "", "", "");
        }
    }

    public record MessageSummary(String messageId, String from, String subject, String snippet) {}

    private static List<String> parseMessageIds(String body) {
        List<String> ids = new ArrayList<>();
        var json = Json.createReader(new StringReader(body)).readObject();
        JsonArray history = json.getJsonArray("history");
        if (history == null) return ids;
        for (var entry : history) {
            JsonArray added = ((JsonObject) entry).getJsonArray("messagesAdded");
            if (added == null) continue;
            for (var msg : added) {
                var message = ((JsonObject) msg).getJsonObject("message");
                if (message != null) ids.add(message.getString("id"));
            }
        }
        return ids;
    }
}
