package io.casehub.connectors.bank.truelayer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

abstract class ConsentTokenStoreContractTest {

    protected abstract ConsentTokenStore createStore();

    private ConsentTokenStore store;

    @BeforeEach
    void setUp() {
        store = createStore();
    }

    @Test
    void storeAndFind_roundTrip() {
        StoredConsent consent = consent("token-1", "refresh-1",
                Instant.now().plusSeconds(3600));
        store.store("user-1", consent);
        StoredConsent found = store.find("user-1");
        assertThat(found.accessToken()).isEqualTo("token-1");
        assertThat(found.refreshToken()).isEqualTo("refresh-1");
        assertThat(found.scopes()).containsExactly(
                ConsentScope.ACCOUNTS, ConsentScope.BALANCE);
    }

    @Test
    void find_unknownUser_returnsNull() {
        assertThat(store.find("nonexistent")).isNull();
    }

    @Test
    void store_overwrites_existingEntry() {
        store.store("user-1", consent("old-token", "refresh",
                Instant.now().plusSeconds(3600)));
        store.store("user-1", consent("new-token", "refresh",
                Instant.now().plusSeconds(3600)));
        assertThat(store.find("user-1").accessToken())
                .isEqualTo("new-token");
    }

    @Test
    void remove_deletesEntry() {
        store.store("user-1", consent("token", "refresh",
                Instant.now().plusSeconds(3600)));
        store.remove("user-1");
        assertThat(store.find("user-1")).isNull();
    }

    @Test
    void remove_unknownUser_noError() {
        store.remove("nonexistent");
    }

    @Test
    void removeExpiredBefore_removesOnlyExpired() {
        Instant now = Instant.now();
        store.store("expired-user", consent("t1", "r1",
                now.minusSeconds(3600)));
        store.store("active-user", consent("t2", "r2",
                now.plusSeconds(86400)));

        int removed = store.removeExpiredBefore(now);

        assertThat(removed).isEqualTo(1);
        assertThat(store.find("expired-user")).isNull();
        assertThat(store.find("active-user")).isNotNull();
    }

    @Test
    void removeExpiredBefore_noneExpired_returnsZero() {
        store.store("active-user", consent("t1", "r1",
                Instant.now().plusSeconds(86400)));
        assertThat(store.removeExpiredBefore(Instant.now())).isZero();
    }

    private StoredConsent consent(String accessToken, String refreshToken,
                                  Instant consentExpiry) {
        return new StoredConsent(accessToken, refreshToken,
                Instant.now().plusSeconds(3600), consentExpiry,
                List.of(ConsentScope.ACCOUNTS, ConsentScope.BALANCE),
                Instant.now());
    }
}
