package io.casehub.connectors.web.ref;

import io.casehub.connectors.web.model.InterventionRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryWebBackend {

    private final Map<String, String> urlToHtml = new ConcurrentHashMap<>();
    private final Map<String, InterventionRequest> interventions = new ConcurrentHashMap<>();
    private final Duration interventionTimeout;

    public InMemoryWebBackend() {
        this(Duration.ofMinutes(10));
    }

    public InMemoryWebBackend(Duration interventionTimeout) {
        this.interventionTimeout = interventionTimeout;
    }

    public void registerUrl(String url, String html) {
        urlToHtml.put(url, html);
    }

    public String fetchHtml(String url) {
        return urlToHtml.get(url);
    }

    public boolean hasUrl(String url) {
        return urlToHtml.containsKey(url);
    }

    public String requestIntervention(String url, String reason) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        interventions.put(id, new InterventionRequest(id, url, reason, null, Instant.now()));
        return id;
    }

    public List<InterventionRequest> pendingInterventions() {
        Instant cutoff = Instant.now().minus(interventionTimeout);
        interventions.entrySet().removeIf(e -> e.getValue().submittedAt().isBefore(cutoff));
        return List.copyOf(interventions.values());
    }

    public void resolveIntervention(String requestId, String html) {
        InterventionRequest request = interventions.remove(requestId);
        if (request != null) {
            urlToHtml.put(request.url(), html);
        }
    }

    public void clear() {
        urlToHtml.clear();
        interventions.clear();
    }
}
