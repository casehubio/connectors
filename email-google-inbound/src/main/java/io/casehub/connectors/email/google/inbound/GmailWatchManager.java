package io.casehub.connectors.email.google.inbound;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.casehub.connectors.http.HttpHelper;
import io.casehub.platform.api.authn.ServiceConnectionProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class GmailWatchManager {

    private static final Logger LOG = Logger.getLogger(GmailWatchManager.class.getName());
    private static final String GMAIL_API = "https://gmail.googleapis.com/gmail/v1/users/me";

    private final ServiceConnectionProvider connectionProvider;
    private final HistoryStore historyStore;
    private final String pubsubTopic;

    @Inject
    public GmailWatchManager(ServiceConnectionProvider connectionProvider,
                              HistoryStore historyStore,
                              @ConfigProperty(name = "casehub.connectors.gmail.push.pubsub-topic",
                                  defaultValue = "") String pubsubTopic) {
        this.connectionProvider = connectionProvider;
        this.historyStore = historyStore;
        this.pubsubTopic = pubsubTopic;
    }

    public void registerWatch(String userId) {
        if (pubsubTopic.isBlank()) {
            LOG.info("gmail-push: pubsub-topic not configured, watch registration skipped");
            return;
        }
        String token = connectionProvider.getAccessToken(userId, "google", null).accessToken();
        String body = "{\"topicName\":\"" + pubsubTopic + "\","
                + "\"labelIds\":[\"INBOX\"]}";
        var req = HttpRequest.newBuilder()
                .uri(URI.create(GMAIL_API + "/watch"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            var resp = HttpHelper.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                var json = Json.createReader(new StringReader(resp.body())).readObject();
                long historyId = json.getJsonNumber("historyId").longValue();
                if (historyStore.getLastHistoryId(userId).isEmpty()) {
                    historyStore.setLastHistoryId(userId, historyId);
                }
                LOG.info("gmail-push: watch registered for " + userId);
            } else {
                LOG.warning("gmail-push: watch API returned " + resp.statusCode()
                        + " for " + userId);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "gmail-push: watch HTTP call failed for " + userId, e);
        }
    }
}
