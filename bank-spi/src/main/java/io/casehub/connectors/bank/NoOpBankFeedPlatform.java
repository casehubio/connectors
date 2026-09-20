package io.casehub.connectors.bank;

import java.time.Instant;
import java.util.List;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.bank.model.AccountBalance;
import io.casehub.connectors.bank.model.AccountInfo;
import io.casehub.connectors.bank.model.Transaction;
import io.casehub.connectors.bank.spi.BankFeedPlatform;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpBankFeedPlatform implements BankFeedPlatform {

    @Override
    public String id() {
        return "none";
    }

    @Override
    public List<AccountInfo> listAccounts() {
        return List.of();
    }

    @Override
    public AccountBalance balance(final String accountId) {
        throw new UnsupportedOperationException("No bank feed provider configured");
    }

    @Override
    public Page<Transaction> listTransactions(final String accountId,
            final Instant from, final Instant to, final PageRequest pagination) {
        return Page.of(List.of());
    }

    @Override
    public Transaction getTransaction(final String accountId,
            final String transactionId) {
        throw new UnsupportedOperationException("No bank feed provider configured");
    }
}
