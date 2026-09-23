package io.casehub.connectors.bank.truelayer;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.bank.model.AccountBalance;
import io.casehub.connectors.bank.model.AccountInfo;
import io.casehub.connectors.bank.model.AccountType;
import io.casehub.connectors.bank.model.Transaction;
import io.casehub.connectors.bank.model.TransactionDirection;
import io.casehub.connectors.bank.model.TransactionStatus;
import io.casehub.connectors.bank.spi.AccountInformation;
import io.casehub.connectors.bank.truelayer.dto.TrueLayerAccount;
import io.casehub.connectors.bank.truelayer.dto.TrueLayerTransaction;
import io.casehub.connectors.bank.truelayer.dto.TrueLayerTransactionPage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class TrueLayerAccountInformation implements AccountInformation {

    private final TrueLayerClient client;
    private final TrueLayerConsentService consentService;
    private final String userId;

    TrueLayerAccountInformation(TrueLayerClient client,
                                 TrueLayerConsentService consentService,
                                 String userId) {
        this.client = client;
        this.consentService = consentService;
        this.userId = userId;
    }

    @Override
    public List<AccountInfo> listAccounts() {
        return client.listAccounts(requireToken()).stream()
                .map(a -> new AccountInfo(a.accountId(), a.displayName(),
                        mapAccountType(a.accountType()), a.currency()))
                .toList();
    }

    @Override
    public AccountBalance balance(String accountId) {
        var b = client.balance(requireToken(), accountId);
        return new AccountBalance(b.accountId(), b.available(), b.current(),
                b.currency(), b.updateTimestamp());
    }

    @Override
    public Page<Transaction> listTransactions(String accountId,
            Instant from, Instant to, PageRequest pagination) {
        var page = client.listTransactions(requireToken(), accountId,
                from.toString(), to.toString(), pagination.cursor());
        List<Transaction> mapped = page.results().stream()
                .map(this::mapTransaction)
                .toList();
        return new Page<>(mapped, page.nextCursor(), page.hasMore());
    }

    @Override
    public Transaction getTransaction(String accountId, String transactionId) {
        var tx = client.getTransaction(requireToken(), accountId, transactionId);
        return mapTransaction(tx);
    }

    private String requireToken() {
        String token = consentService.getUserToken(userId);
        if (token == null) {
            throw new ConsentExpiredException(userId);
        }
        return token;
    }

    private Transaction mapTransaction(TrueLayerTransaction tx) {
        BigDecimal amount = tx.amount().abs();
        TransactionDirection direction = tx.amount().signum() < 0
                ? TransactionDirection.DEBIT : TransactionDirection.CREDIT;
        TransactionStatus status = "Booked".equalsIgnoreCase(tx.status())
                ? TransactionStatus.BOOKED : TransactionStatus.PENDING;

        return new Transaction(tx.transactionId(), tx.accountId(),
                amount, direction, tx.currency(),
                tx.description(), tx.merchantName(),
                tx.transactionCategory(),
                tx.timestamp(), status);
    }

    private AccountType mapAccountType(String tlType) {
        return switch (tlType) {
            case "TRANSACTION" -> AccountType.CURRENT;
            case "SAVINGS" -> AccountType.SAVINGS;
            case "CREDIT_CARD" -> AccountType.CREDIT_CARD;
            default -> AccountType.OTHER;
        };
    }
}
