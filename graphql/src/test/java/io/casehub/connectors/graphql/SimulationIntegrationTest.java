package io.casehub.connectors.graphql;

import io.casehub.connectors.Page;
import io.casehub.connectors.bank.model.AccountBalance;
import io.casehub.connectors.bank.model.AccountInfo;
import io.casehub.connectors.bank.model.AccountType;
import io.casehub.connectors.bank.model.Transaction;
import io.casehub.connectors.email.model.EmailMessage;
import io.casehub.connectors.email.model.Mailbox;
import io.casehub.platform.simulation.Simulation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static io.casehub.connectors.bank.model.TransactionDirection.DEBIT;
import static io.casehub.connectors.bank.model.TransactionStatus.BOOKED;
import static org.assertj.core.api.Assertions.assertThat;

class SimulationIntegrationTest {

    private Simulation sim;

    @BeforeEach
    void setUp() {
        var account = new AccountInfo("acc-001", "Current Account",
                AccountType.CURRENT, "GBP");
        var balance = new AccountBalance("acc-001",
                new BigDecimal("2847.63"), new BigDecimal("3147.63"),
                "GBP", Instant.parse("2026-09-20T08:00:00Z"));
        var transaction = new Transaction("txn-001", "acc-001",
                new BigDecimal("45.80"), DEBIT, "GBP",
                "Tesco Superstore", "Tesco", "groceries",
                LocalDate.of(2026, 9, 19), BOOKED);
        var mailbox = new Mailbox("inbox", "Inbox", 3);

        sim = Simulation.forTest("household")
                .seed("bank-feed-platform.listAccounts", null, List.of(account))
                .stub("bank-feed-platform.balance", "acc-001", balance)
                .seed("bank-feed-platform.listTransactions", null,
                        Page.of(List.of(transaction)))
                .stub("bank-feed-platform.getTransaction", "txn-001", transaction)
                .seed("email-platform.listMailboxes", null, List.of(mailbox))
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void bankFeed_listAccounts_resolvesFromCorpus() {
        List<AccountInfo> accounts = sim.resolve(
                "bank-feed-platform.listAccounts", null);
        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).id()).isEqualTo("acc-001");
        assertThat(accounts.get(0).currency()).isEqualTo("GBP");
    }

    @Test
    void bankFeed_balance_resolvesViaKeyLookup() {
        AccountBalance balance = sim.resolve(
                "bank-feed-platform.balance", "acc-001");
        assertThat(balance.accountId()).isEqualTo("acc-001");
        assertThat(balance.available()).isEqualByComparingTo("2847.63");
        assertThat(balance.current()).isEqualByComparingTo("3147.63");
    }

    @Test
    @SuppressWarnings("unchecked")
    void bankFeed_listTransactions_resolvesSequentially() {
        Page<Transaction> page = sim.resolve(
                "bank-feed-platform.listTransactions", null);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).merchantName()).isEqualTo("Tesco");
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void bankFeed_getTransaction_resolvesViaKeyLookup() {
        Transaction txn = sim.resolve(
                "bank-feed-platform.getTransaction", "txn-001");
        assertThat(txn.id()).isEqualTo("txn-001");
        assertThat(txn.amount()).isEqualByComparingTo("45.80");
        assertThat(txn.direction()).isEqualTo(DEBIT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void email_listMailboxes_resolvesFromCorpus() {
        List<Mailbox> mailboxes = sim.resolve(
                "email-platform.listMailboxes", null);
        assertThat(mailboxes).hasSize(1);
        assertThat(mailboxes.get(0).name()).isEqualTo("Inbox");
        assertThat(mailboxes.get(0).unreadCount()).isEqualTo(3);
    }

    @Test
    void journalRecordsAllInvocations() {
        var overlay = sim.overlay();

        sim.resolve("bank-feed-platform.listAccounts", null);
        sim.resolve("bank-feed-platform.balance", "acc-001");
        sim.resolve("email-platform.listMailboxes", null);

        var verifier = sim.verifier();
        verifier.method("bank-feed-platform.listAccounts")
                .wasCalled(1);
        verifier.method("bank-feed-platform.balance")
                .wasCalled(1);
        verifier.method("email-platform.listMailboxes")
                .wasCalled(1);
        verifier.inOrder(
                "bank-feed-platform.listAccounts",
                "bank-feed-platform.balance",
                "email-platform.listMailboxes");

        sim.popOverlay(overlay);
    }
}
