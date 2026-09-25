package io.casehub.connectors.bank.ref;

import java.time.Instant;
import java.util.List;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.bank.model.AccountBalance;
import io.casehub.connectors.bank.model.AccountInfo;
import io.casehub.connectors.bank.model.Transaction;
import io.casehub.connectors.bank.spi.AccountInformation;

class RefAccountInformation implements AccountInformation {

    private final BankBackend backend;

    RefAccountInformation(BankBackend backend) {
        this.backend = backend;
    }

    @Override
    public List<AccountInfo> listAccounts() {
        return backend.listAccounts();
    }

    @Override
    public AccountBalance balance(String accountId) {
        return backend.balance(accountId);
    }

    @Override
    public Page<Transaction> listTransactions(String accountId,
                                               Instant from, Instant to,
                                               PageRequest pagination) {
        return backend.listTransactions(accountId, from, to, pagination);
    }

    @Override
    public Transaction getTransaction(String accountId, String transactionId) {
        return backend.getTransaction(accountId, transactionId);
    }
}
