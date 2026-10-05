package io.casehub.connectors.bank.ref;

import io.casehub.connectors.bank.model.AccountType;
import io.casehub.connectors.bank.model.TransactionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedLoaderTest {

    @Test
    void loadsThreeAccounts() {
        var accounts = SeedLoader.loadAccounts();
        assertThat(accounts).hasSize(3);
        assertThat(accounts).extracting("type")
                .containsExactly(AccountType.CURRENT, AccountType.SAVINGS, AccountType.CREDIT_CARD);
    }

    @Test
    void loadsThreeBalances() {
        var balances = SeedLoader.loadBalances();
        assertThat(balances).hasSize(3);
        assertThat(balances.get("acc-100").currency()).isEqualTo("GBP");
        assertThat(balances.get("acc-100").available()).isEqualByComparingTo("2450.00");
    }

    @Test
    void loadsTwelveTransactions() {
        var txns = SeedLoader.loadTransactions();
        assertThat(txns).hasSize(12);
    }

    @Test
    void lastTransactionIsPending() {
        var txns = SeedLoader.loadTransactions();
        assertThat(txns.getLast().status()).isEqualTo(TransactionStatus.PENDING);
    }
}
