package io.casehub.connectors.bank.ref;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.bank.model.AccountBalance;
import io.casehub.connectors.bank.model.AccountInfo;
import io.casehub.connectors.bank.model.AccountType;
import io.casehub.connectors.bank.model.InitiatedPayment;
import io.casehub.connectors.bank.model.PaymentRequest;
import io.casehub.connectors.bank.model.PaymentStatus;
import io.casehub.connectors.bank.model.Transaction;
import io.casehub.connectors.bank.model.TransactionDirection;
import io.casehub.connectors.bank.model.TransactionStatus;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class InMemoryBankBackend implements BankBackend {

    private static final List<AccountInfo> ACCOUNTS = List.of(
            new AccountInfo("acc-100", "Current Account", AccountType.CURRENT, "GBP"),
            new AccountInfo("acc-200", "Savings Account", AccountType.SAVINGS, "GBP"),
            new AccountInfo("acc-300", "Credit Card", AccountType.CREDIT_CARD, "GBP"));

    private static final Map<String, AccountBalance> BALANCES = Map.of(
            "acc-100", new AccountBalance("acc-100",
                    new BigDecimal("2450.00"), new BigDecimal("2450.00"),
                    "GBP", Instant.parse("2026-09-25T00:00:00Z")),
            "acc-200", new AccountBalance("acc-200",
                    new BigDecimal("15000.00"), new BigDecimal("15000.00"),
                    "GBP", Instant.parse("2026-09-25T00:00:00Z")),
            "acc-300", new AccountBalance("acc-300",
                    new BigDecimal("4750.00"), new BigDecimal("5000.00"),
                    "GBP", Instant.parse("2026-09-25T00:00:00Z")));

    private static final List<Transaction> TRANSACTIONS = List.of(
            new Transaction("txn-001", "acc-100", new BigDecimal("3.50"),
                    TransactionDirection.DEBIT, "GBP", "Grocery purchase",
                    "Tesco Express", "Groceries",
                    LocalDate.of(2026, 9, 1), TransactionStatus.BOOKED),
            new Transaction("txn-002", "acc-100", new BigDecimal("45.00"),
                    TransactionDirection.DEBIT, "GBP", "Fuel",
                    "Shell Garage", "Transport",
                    LocalDate.of(2026, 9, 2), TransactionStatus.BOOKED),
            new Transaction("txn-003", "acc-100", new BigDecimal("2500.00"),
                    TransactionDirection.CREDIT, "GBP", "Monthly salary",
                    "ACME Corp", "Salary",
                    LocalDate.of(2026, 9, 3), TransactionStatus.BOOKED),
            new Transaction("txn-004", "acc-100", new BigDecimal("12.99"),
                    TransactionDirection.DEBIT, "GBP", "Subscription",
                    "Netflix", "Entertainment",
                    LocalDate.of(2026, 9, 5), TransactionStatus.BOOKED),
            new Transaction("txn-005", "acc-100", new BigDecimal("67.50"),
                    TransactionDirection.DEBIT, "GBP", "Weekly shop",
                    "Sainsburys", "Groceries",
                    LocalDate.of(2026, 9, 7), TransactionStatus.BOOKED),
            new Transaction("txn-006", "acc-100", new BigDecimal("150.00"),
                    TransactionDirection.DEBIT, "GBP", "Energy bill",
                    "British Gas", "Utilities",
                    LocalDate.of(2026, 9, 10), TransactionStatus.BOOKED),
            new Transaction("txn-007", "acc-100", new BigDecimal("8.90"),
                    TransactionDirection.DEBIT, "GBP", "Coffee",
                    "Costa Coffee", "Dining",
                    LocalDate.of(2026, 9, 12), TransactionStatus.BOOKED),
            new Transaction("txn-008", "acc-100", new BigDecimal("35.00"),
                    TransactionDirection.DEBIT, "GBP", "Online order",
                    "Amazon UK", "Shopping",
                    LocalDate.of(2026, 9, 14), TransactionStatus.BOOKED),
            new Transaction("txn-009", "acc-100", new BigDecimal("500.00"),
                    TransactionDirection.DEBIT, "GBP", "Savings transfer",
                    "Nationwide BS", "Transfers",
                    LocalDate.of(2026, 9, 15), TransactionStatus.BOOKED),
            new Transaction("txn-010", "acc-100", new BigDecimal("22.50"),
                    TransactionDirection.DEBIT, "GBP", "Food delivery",
                    "Deliveroo", "Dining",
                    LocalDate.of(2026, 9, 18), TransactionStatus.BOOKED),
            new Transaction("txn-011", "acc-100", new BigDecimal("9.99"),
                    TransactionDirection.DEBIT, "GBP", "Music subscription",
                    "Spotify", "Entertainment",
                    LocalDate.of(2026, 9, 20), TransactionStatus.BOOKED),
            new Transaction("txn-012", "acc-100", new BigDecimal("75.00"),
                    TransactionDirection.DEBIT, "GBP", "Travel card",
                    "TfL", "Transport",
                    LocalDate.of(2026, 9, 22), TransactionStatus.PENDING));

    private final ConcurrentHashMap<String, PaymentState> payments = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> idempotencyIndex = new ConcurrentHashMap<>();

    @Override
    public List<AccountInfo> listAccounts() {
        return ACCOUNTS;
    }

    @Override
    public AccountBalance balance(String accountId) {
        AccountBalance bal = BALANCES.get(accountId);
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
        List<Transaction> filtered = TRANSACTIONS.stream()
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
        return TRANSACTIONS.stream()
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
