package io.casehub.connectors.bank;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.bank.model.AccountBalance;
import io.casehub.connectors.bank.model.AccountInfo;
import io.casehub.connectors.bank.model.Transaction;
import io.casehub.connectors.bank.spi.BankFeedPlatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankFeedPlatformServiceTest {

    static class StubPlatform implements BankFeedPlatform {
        private final String platformId;
        StubPlatform(String id) { this.platformId = id; }
        @Override public String id() { return platformId; }
        @Override public List<AccountInfo> listAccounts() { return List.of(); }
        @Override public AccountBalance balance(String accountId) { return null; }
        @Override public Page<Transaction> listTransactions(String accountId,
                Instant from, Instant to, PageRequest pagination) { return Page.of(List.of()); }
        @Override public Transaction getTransaction(String accountId, String transactionId) { return null; }
    }

    @Test
    void platform_knownId_returnsPlatform() {
        var service = new BankFeedPlatformService(List.of(new StubPlatform("truelayer")));
        assertThat(service.platform("truelayer").id()).isEqualTo("truelayer");
    }

    @Test
    void platform_unknownId_throws() {
        var service = new BankFeedPlatformService(List.of(new StubPlatform("truelayer")));
        assertThatThrownBy(() -> service.platform("plaid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plaid")
                .hasMessageContaining("truelayer");
    }

    @Test
    void supports_knownId_returnsTrue() {
        var service = new BankFeedPlatformService(List.of(new StubPlatform("truelayer")));
        assertThat(service.supports("truelayer")).isTrue();
    }

    @Test
    void supports_unknownId_returnsFalse() {
        var service = new BankFeedPlatformService(List.of(new StubPlatform("truelayer")));
        assertThat(service.supports("plaid")).isFalse();
    }

    @Test
    void ids_returnsAllRegistered() {
        var service = new BankFeedPlatformService(
                List.of(new StubPlatform("truelayer"), new StubPlatform("plaid")));
        assertThat(service.ids()).containsExactlyInAnyOrder("truelayer", "plaid");
    }

    @Test
    void duplicateId_throwsAtConstruction() {
        assertThatThrownBy(() -> new BankFeedPlatformService(
                List.of(new StubPlatform("truelayer"), new StubPlatform("truelayer"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("truelayer");
    }
}
