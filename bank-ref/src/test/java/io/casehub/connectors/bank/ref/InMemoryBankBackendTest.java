package io.casehub.connectors.bank.ref;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.bank.model.AccountType;
import io.casehub.connectors.bank.model.TransactionDirection;
import io.casehub.connectors.bank.model.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryBankBackendTest {

    private InMemoryBankBackend backend;

    @BeforeEach
    void setUp() {
        backend = new InMemoryBankBackend();
    }

    @Test
    void listAccounts_returnsThreeAccounts() {
        var accounts = backend.listAccounts();
        assertThat(accounts).hasSize(3);
        assertThat(accounts).extracting("id")
                .containsExactlyInAnyOrder("acc-100", "acc-200", "acc-300");
    }

    @Test
    void listAccounts_hasCorrectTypes() {
        var accounts = backend.listAccounts();
        assertThat(accounts).extracting("type")
                .containsExactlyInAnyOrder(
                        AccountType.CURRENT, AccountType.SAVINGS, AccountType.CREDIT_CARD);
    }

    @Test
    void listAccounts_allGbp() {
        var accounts = backend.listAccounts();
        assertThat(accounts).allMatch(a -> "GBP".equals(a.currency()));
    }

    @Test
    void balance_existingAccount_returnsBalance() {
        var balance = backend.balance("acc-100");
        assertThat(balance.accountId()).isEqualTo("acc-100");
        assertThat(balance.currency()).isEqualTo("GBP");
        assertThat(balance.available()).isNotNull();
        assertThat(balance.current()).isNotNull();
        assertThat(balance.asOf()).isNotNull();
    }

    @Test
    void balance_unknownAccount_throws() {
        assertThatThrownBy(() -> backend.balance("unknown"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void listTransactions_fullRange_returnsAll() {
        var page = backend.listTransactions("acc-100",
                                            Instant.parse("2026-01-01T00:00:00Z"),
                                            Instant.parse("2026-12-31T00:00:00Z"),
                                            PageRequest.first(200));
        assertThat(page.items()).hasSizeGreaterThanOrEqualTo(50);
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void listTransactions_narrowRange_filters() {
        var page = backend.listTransactions("acc-100",
                                            Instant.parse("2026-10-05T00:00:00Z"),
                                            Instant.parse("2026-10-06T00:00:00Z"),
                                            PageRequest.first(50));
        assertThat(page.items()).isNotEmpty();
        assertThat(page.items()).allMatch(t ->
                                                  !t.date().isBefore(LocalDate.of(2026, 10, 5))
                                                  && t.date().isBefore(LocalDate.of(2026, 10, 6)));
    }

    @Test
    void listTransactions_pagination_respectsPageSize() {
        var page1 = backend.listTransactions("acc-100",
                                             Instant.parse("2026-01-01T00:00:00Z"),
                                             Instant.parse("2026-12-31T00:00:00Z"),
                                             PageRequest.first(10));
        assertThat(page1.items()).hasSize(10);
        assertThat(page1.hasMore()).isTrue();
        assertThat(page1.nextCursor()).isNotNull();

        var page2 = backend.listTransactions("acc-100",
                                             Instant.parse("2026-01-01T00:00:00Z"),
                                             Instant.parse("2026-12-31T00:00:00Z"),
                                             new PageRequest(page1.nextCursor(), 10));
        assertThat(page2.items()).hasSize(10);
        assertThat(page2.hasMore()).isTrue();
    }

    @Test
    void listTransactions_emptyAccount_returnsEmpty() {
        var page = backend.listTransactions("acc-200",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(50));
        assertThat(page.items()).isEmpty();
    }

    @Test
    void getTransaction_existing_returnsTransaction() {
        var txn = backend.getTransaction("acc-100", "txn-001");
        assertThat(txn.id()).isEqualTo("txn-001");
        assertThat(txn.accountId()).isEqualTo("acc-100");
        assertThat(txn.merchantName()).isEqualTo("Meridian Engineering");
        assertThat(txn.direction()).isEqualTo(TransactionDirection.CREDIT);
        assertThat(txn.status()).isEqualTo(TransactionStatus.BOOKED);
    }

    @Test
    void getTransaction_unknownTransaction_throws() {
        assertThatThrownBy(() -> backend.getTransaction("acc-100", "unknown"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getTransaction_unknownAccount_throws() {
        assertThatThrownBy(() -> backend.getTransaction("unknown", "txn-001"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void transactions_haveMerchantAndCategory() {
        var page = backend.listTransactions("acc-100",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(50));
        assertThat(page.items()).allMatch(t -> t.merchantName() != null);
        assertThat(page.items()).allMatch(t -> t.category() != null);
    }

    @Test
    void transactions_includeDebitAndCredit() {
        var page = backend.listTransactions("acc-100",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(50));
        assertThat(page.items()).extracting("direction")
                .contains(TransactionDirection.DEBIT, TransactionDirection.CREDIT);
    }

    @Test
    void transactions_includeBookedAndPending() {
        var page = backend.listTransactions("acc-100",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(50));
        assertThat(page.items()).extracting("status")
                .contains(TransactionStatus.BOOKED, TransactionStatus.PENDING);
    }
}
