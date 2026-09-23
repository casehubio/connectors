package io.casehub.connectors.bank.truelayer;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Alternative
@Priority(100)
@ApplicationScoped
public class InMemoryConsentTokenStore implements ConsentTokenStore {

    private final Map<String, StoredConsent> store = new ConcurrentHashMap<>();

    @Override
    public void store(String userId, StoredConsent consent) {
        store.put(userId, consent);
    }

    @Override
    public StoredConsent find(String userId) {
        return store.get(userId);
    }

    @Override
    public void remove(String userId) {
        store.remove(userId);
    }

    @Override
    public int removeExpiredBefore(Instant cutoff) {
        int[] count = {0};
        store.entrySet().removeIf(e -> {
            if (e.getValue().consentExpiry().isBefore(cutoff)) {
                count[0]++;
                return true;
            }
            return false;
        });
        return count[0];
    }
}
