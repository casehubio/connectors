package io.casehub.connectors.bank.truelayer;

import java.time.Instant;

public interface ConsentTokenStore {

    void store(String userId, StoredConsent consent);

    StoredConsent find(String userId);

    void remove(String userId);

    int removeExpiredBefore(Instant cutoff);
}
