package io.casehub.connectors.bank;

import java.time.Instant;
import java.util.List;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.bank.model.AccountBalance;
import io.casehub.connectors.bank.model.AccountInfo;
import io.casehub.connectors.bank.model.Transaction;
import io.casehub.connectors.bank.spi.AccountInformation;

public class NoOpAccountInformation implements AccountInformation {

    public static final NoOpAccountInformation INSTANCE = new NoOpAccountInformation();

    @Override
    public List<AccountInfo> listAccounts() {
        return List.of();
    }

    @Override
    public AccountBalance balance(String accountId) {
        throw new UnsupportedOperationException("No bank platform configured");
    }

    @Override
    public Page<Transaction> listTransactions(String accountId,
            Instant from, Instant to, PageRequest pagination) {
        return Page.of(List.of());
    }

    @Override
    public Transaction getTransaction(String accountId, String transactionId) {
        throw new UnsupportedOperationException("No bank platform configured");
    }
}
