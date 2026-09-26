package io.casehub.connectors.bank.truelayer;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WebhookEventDeduplicator {

    private static final long TTL_SECONDS = 600;
    private final ConcurrentHashMap<String, Instant> seen = new ConcurrentHashMap<>();

    public boolean isDuplicate(String eventId) {
        if (eventId == null) return false;
        Instant previous = seen.putIfAbsent(eventId, Instant.now());
        return previous != null;
    }

    @Scheduled(every = "5m")
    void cleanup() {
        Instant cutoff = Instant.now().minusSeconds(TTL_SECONDS);
        seen.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }
}
