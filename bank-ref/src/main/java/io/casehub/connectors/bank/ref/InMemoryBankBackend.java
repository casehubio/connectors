package io.casehub.connectors.bank.ref;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.bank.model.AccountBalance;
import io.casehub.connectors.bank.model.AccountInfo;
import io.casehub.connectors.bank.model.InitiatedPayment;
import io.casehub.connectors.bank.model.PaymentRequest;
import io.casehub.connectors.bank.model.PaymentStatus;
import io.casehub.connectors.bank.model.Transaction;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@DefaultBean
@ApplicationScoped
public class InMemoryBankBackend implements BankBackend {

    private final List<AccountInfo> accounts = SeedLoader.loadAccounts();

    private final Map<String, AccountBalance> balances = SeedLoader.loadBalances();

    private final List<Transaction> transactions = SeedLoader.loadTransactions();

    private final ConcurrentHashMap<String, PaymentState> payments = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> idempotencyIndex = new ConcurrentHashMap<>();

    @Override
    public List<AccountInfo> listAccounts() {
        return accounts;
    }

    @Override
    public AccountBalance balance(String accountId) {
        AccountBalance bal = balances.get(accountId);
        if (bal == null) {
            throw new NoSuchElementException(
                    "No account with id '" + accountId + "'");
        }
        return bal;
    }

    @Override
    public Page<Transaction> listTransactions(String accountId,
                                               Instant from, Instant to,
                                               PageRequest pagination) {
        List<Transaction> filtered = transactions.stream()
                .filter(t -> t.accountId().equals(accountId))
                .filter(t -> {
                    Instant txnInstant = t.date().atStartOfDay(ZoneOffset.UTC).toInstant();
                    return !txnInstant.isBefore(from) && txnInstant.isBefore(to);
                })
                .toList();

        int offset = pagination.cursor() != null
                ? Integer.parseInt(pagination.cursor()) : 0;
        int end = Math.min(offset + pagination.pageSize(), filtered.size());
        List<Transaction> page = filtered.subList(offset, end);
        boolean hasMore = end < filtered.size();
        String nextCursor = hasMore ? String.valueOf(end) : null;
        return new Page<>(page, nextCursor, hasMore);
    }

    @Override
    public Transaction getTransaction(String accountId, String transactionId) {
        return transactions.stream()
                .filter(t -> t.accountId().equals(accountId)
                        && t.id().equals(transactionId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "No transaction '" + transactionId
                        + "' in account '" + accountId + "'"));
    }

    @Override
    public InitiatedPayment initiatePayment(PaymentRequest payment) {
        String existing = idempotencyIndex.get(payment.idempotencyKey());
        if (existing != null) {
            PaymentState state = payments.get(existing);
            return new InitiatedPayment(existing,
                    "https://ref.bank.example/pay/" + existing,
                    state.status);
        }

        String paymentId = UUID.randomUUID().toString();
        payments.put(paymentId, new PaymentState(PaymentStatus.AUTHORIZATION_REQUIRED));
        idempotencyIndex.put(payment.idempotencyKey(), paymentId);
        return new InitiatedPayment(paymentId,
                "https://ref.bank.example/pay/" + paymentId,
                PaymentStatus.AUTHORIZATION_REQUIRED);
    }

    @Override
    public PaymentStatus paymentStatus(String paymentId) {
        PaymentState state = payments.get(paymentId);
        if (state == null) {
            throw new NoSuchElementException(
                    "No payment with id '" + paymentId + "'");
        }
        PaymentStatus current = state.status;
        state.advance();
        return current;
    }

    private static final class PaymentState {
        private static final PaymentStatus[] PROGRESSION = {
                PaymentStatus.AUTHORIZATION_REQUIRED,
                PaymentStatus.EXECUTED,
                PaymentStatus.SETTLED
        };
        PaymentStatus status;
        private int index;

        PaymentState(PaymentStatus initial) {
            this.status = initial;
            this.index = 0;
        }

        void advance() {
            if (index < PROGRESSION.length - 1) {
                index++;
                status = PROGRESSION[index];
            }
        }
    }
}
