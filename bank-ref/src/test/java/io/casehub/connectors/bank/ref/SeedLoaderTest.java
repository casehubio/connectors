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
        assertThat(balances.get("acc-100").available()).isEqualByComparingTo("3245.67");
    }

    @Test
    void loadsLifeDomainTransactions() {
        var txns = SeedLoader.loadTransactions();
        assertThat(txns).hasSizeGreaterThanOrEqualTo(60);
        assertThat(txns).extracting("merchantName")
                        .contains("Meridian Engineering", "Camden NHS Trust", "Halifax", "British Gas");
    }

    @Test
    void includesBookedAndPendingTransactions() {
        var txns = SeedLoader.loadTransactions();
        assertThat(txns).extracting("status")
                        .contains(TransactionStatus.BOOKED, TransactionStatus.PENDING);
    }
}
