package io.casehub.connectors.bank.truelayer;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentCleanupJobTest {

    @Test
    void run_removesExpiredConsents_leavesActiveOnes() {
        InMemoryConsentTokenStore store = new InMemoryConsentTokenStore();
        Instant now = Instant.now();

        store.store("expired-1", new StoredConsent(
                "t1", "r1", now.minusSeconds(7200),
                now.minusSeconds(3600),
                List.of(ConsentScope.ACCOUNTS), now.minusSeconds(86400)));
        store.store("expired-2", new StoredConsent(
                "t2", "r2", now.minusSeconds(7200),
                now.minusSeconds(60),
                List.of(ConsentScope.ACCOUNTS), now.minusSeconds(86400)));
        store.store("active", new StoredConsent(
                "t3", "r3", now.plusSeconds(3600),
                now.plusSeconds(86400L * 90),
                List.of(ConsentScope.ACCOUNTS, ConsentScope.BALANCE),
                now));

        ConsentCleanupJob job = new ConsentCleanupJob(store);
        job.run();

        assertThat(store.find("expired-1")).isNull();
        assertThat(store.find("expired-2")).isNull();
        assertThat(store.find("active")).isNotNull();
        assertThat(store.find("active").accessToken()).isEqualTo("t3");
    }

    @Test
    void run_noExpired_noErrors() {
        InMemoryConsentTokenStore store = new InMemoryConsentTokenStore();
        store.store("active", new StoredConsent(
                "t1", "r1", Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(86400L * 90),
                List.of(ConsentScope.ACCOUNTS), Instant.now()));

        ConsentCleanupJob job = new ConsentCleanupJob(store);
        job.run();

        assertThat(store.find("active")).isNotNull();
    }
}
