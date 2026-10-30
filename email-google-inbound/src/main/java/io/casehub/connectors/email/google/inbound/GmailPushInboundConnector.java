package io.casehub.connectors.email.google.inbound;

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.WebhookInboundConnector;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;
import jakarta.json.Json;
import jakarta.json.JsonObject;

public class GmailPushInboundConnector extends WebhookInboundConnector {

    private static final Logger LOG = Logger.getLogger(GmailPushInboundConnector.class.getName());

    private final HistoryStore historyStore;
    private final GmailHistoryClient historyClient;
    private final String expectedAudience;
    private final ConcurrentHashMap<String, ReentrantLock> emailLocks = new ConcurrentHashMap<>();

    public GmailPushInboundConnector(HistoryStore historyStore,
                                      GmailHistoryClient historyClient,
                                      String expectedAudience) {
        this.historyStore = historyStore;
        this.historyClient = historyClient;
        this.expectedAudience = expectedAudience;
    }

    @Override
    public String id() {
        return InboundConnectorIds.GMAIL_PUSH;
    }

    @Override
    public WebhookResult handle(WebhookRequest request) {
        try {
            return doHandle(request);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "gmail-push: unexpected error", e);
            return new WebhookResult.Ignored();
        }
    }

    private WebhookResult doHandle(WebhookRequest request) {
        String jwt = extractJwt(request);
        if (jwt == null || !validateJwt(jwt)) {
            return new WebhookResult.Unauthorized();
        }

        var notification = parseNotification(request.body());
        if (notification == null) return new WebhookResult.Ignored();

        String emailAddress = notification.getString("emailAddress", null);
        if (emailAddress == null) return new WebhookResult.Ignored();

        return new WebhookResult.DeferredDelivery(List.of(),
                () -> fetchNewMessages(emailAddress));
    }

    private List<InboundMessage> fetchNewMessages(String emailAddress) {
        var lock = emailLocks.computeIfAbsent(emailAddress, k -> new ReentrantLock());
        lock.lock();
        try {
            var lastId = historyStore.getLastHistoryId(emailAddress);
            if (lastId.isEmpty()) {
                long current = historyClient.getCurrentHistoryId(emailAddress);
                historyStore.setLastHistoryId(emailAddress, current);
                return List.of();
            }

            var historyResult = historyClient.getNewMessages(emailAddress, lastId.get());
            if (historyResult.messageIds().isEmpty()) {
                historyStore.setLastHistoryId(emailAddress, historyResult.historyId());
                return List.of();
            }

            List<InboundMessage> messages = new ArrayList<>();
            for (String msgId : historyResult.messageIds()) {
                try {
                    var summary = historyClient.getMessageSummary(emailAddress, msgId);
                    messages.add(new InboundMessage(
                            InboundConnectorIds.GMAIL_PUSH,
                            InboundConnectorTypes.EMAIL,
                            summary.from(), emailAddress,
                            summary.snippet(), List.of(),
                            Instant.now(),
                            Map.of("message-id", msgId, "subject", summary.subject()),
                            null));
                } catch (Exception e) {
                    LOG.warning("gmail-push: failed to fetch message " + msgId
                            + " for " + emailAddress + ": " + e.getMessage());
                }
            }

            historyStore.setLastHistoryId(emailAddress, historyResult.historyId());
            return messages;
        } finally {
            lock.unlock();
        }
    }

    private static String extractJwt(WebhookRequest request) {
        String auth = request.header("authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7);
        return null;
    }

    boolean validateJwt(String jwt) {
        if (expectedAudience == null || expectedAudience.isBlank()) {
            LOG.warning("gmail-push: no audience configured — skipping JWT validation");
            return true;
        }
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return false;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            var claims = Json.createReader(new StringReader(payload)).readObject();
            String iss = claims.getString("iss", "");
            if (!"accounts.google.com".equals(iss)
                    && !"https://accounts.google.com".equals(iss)) {
                return false;
            }
            String aud = claims.getString("aud", "");
            if (!expectedAudience.equals(aud)) return false;
            long exp = claims.getJsonNumber("exp").longValue();
            return Instant.now().getEpochSecond() < exp;
        } catch (Exception e) {
            LOG.warning("gmail-push: JWT validation failed: " + e.getMessage());
            return false;
        }
    }

    private static JsonObject parseNotification(String body) {
        try {
            var root = Json.createReader(new StringReader(body)).readObject();
            var message = root.getJsonObject("message");
            if (message == null) return null;
            String data = message.getString("data", null);
            if (data == null) return null;
            String decoded = new String(Base64.getUrlDecoder().decode(data));
            return Json.createReader(new StringReader(decoded)).readObject();
        } catch (Exception e) {
            return null;
        }
    }
}
