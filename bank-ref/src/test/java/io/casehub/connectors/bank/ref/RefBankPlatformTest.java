package io.casehub.connectors.bank.ref;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.bank.model.PaymentDestination;
import io.casehub.connectors.bank.model.PaymentRequest;
import io.casehub.connectors.bank.model.PaymentStatus;
import io.casehub.connectors.bank.spi.AccountInformation;
import io.casehub.connectors.bank.spi.PaymentInitiation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefBankPlatformTest {

    private RefBankPlatform platform;

    @BeforeEach
    void setUp() {
        platform = new RefBankPlatform(new InMemoryBankBackend());
    }

    @Test
    void id_isRef() {
        assertThat(platform.id()).isEqualTo("ref");
    }

    @Test
    void supports_accountInformation() {
        assertThat(platform.supports(AccountInformation.class)).isTrue();
    }

    @Test
    void supports_paymentInitiation() {
        assertThat(platform.supports(PaymentInitiation.class)).isTrue();
    }

    @Test
    void supports_unknownCapability_returnsFalse() {
        assertThat(platform.supports(Runnable.class)).isFalse();
    }

    @Test
    void accountInformation_listAccounts() {
        var accounts = platform.accountInformation("user-1").listAccounts();
        assertThat(accounts).hasSize(3);
    }

    @Test
    void accountInformation_balance() {
        var balance = platform.accountInformation("user-1").balance("acc-100");
        assertThat(balance.accountId()).isEqualTo("acc-100");
        assertThat(balance.currency()).isEqualTo("GBP");
    }

    @Test
    void accountInformation_balance_unknownAccount() {
        assertThatThrownBy(() ->
                platform.accountInformation("user-1").balance("unknown"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void accountInformation_listTransactions() {
        var page = platform.accountInformation("user-1")
                .listTransactions("acc-100",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-12-31T00:00:00Z"),
                        PageRequest.first(50));
        assertThat(page.items()).hasSize(12);
    }

    @Test
    void accountInformation_getTransaction() {
        var txn = platform.accountInformation("user-1")
                .getTransaction("acc-100", "txn-001");
        assertThat(txn.merchantName()).isEqualTo("Tesco Express");
    }

    @Test
    void accountInformation_getTransaction_unknown() {
        assertThatThrownBy(() ->
                platform.accountInformation("user-1")
                        .getTransaction("acc-100", "unknown"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void paymentInitiation_initiatePayment() {
        var payment = new PaymentRequest("key-1",
                new BigDecimal("100.00"), "GBP", "Test Payee",
                new PaymentDestination.UkAccount("12-34-56", "12345678"),
                "Test payment");
        var result = platform.paymentInitiation("user-1")
                .initiatePayment(payment);
        assertThat(result.paymentId()).isNotNull();
        assertThat(result.hostedPaymentPageLink()).contains(result.paymentId());
        assertThat(result.status()).isEqualTo(PaymentStatus.AUTHORIZATION_REQUIRED);
    }

    @Test
    void paymentInitiation_paymentStatus_advancesOnPoll() {
        var payment = new PaymentRequest("key-2",
                new BigDecimal("50.00"), "GBP", "Test Payee",
                new PaymentDestination.UkAccount("12-34-56", "12345678"),
                "Test payment");
        var initiated = platform.paymentInitiation("user-1")
                .initiatePayment(payment);
        String paymentId = initiated.paymentId();

        var pi = platform.paymentInitiation("user-1");
        assertThat(pi.paymentStatus(paymentId)).isEqualTo(PaymentStatus.AUTHORIZATION_REQUIRED);
        assertThat(pi.paymentStatus(paymentId)).isEqualTo(PaymentStatus.EXECUTED);
        assertThat(pi.paymentStatus(paymentId)).isEqualTo(PaymentStatus.SETTLED);
        assertThat(pi.paymentStatus(paymentId)).isEqualTo(PaymentStatus.SETTLED);
    }

    @Test
    void paymentInitiation_paymentStatus_unknownPayment() {
        assertThatThrownBy(() ->
                platform.paymentInitiation("user-1")
                        .paymentStatus("unknown"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void paymentInitiation_idempotency() {
        var payment = new PaymentRequest("key-3",
                new BigDecimal("75.00"), "GBP", "Test Payee",
                new PaymentDestination.UkAccount("12-34-56", "12345678"),
                "Test payment");
        var pi = platform.paymentInitiation("user-1");
        var first = pi.initiatePayment(payment);
        var second = pi.initiatePayment(payment);
        assertThat(second.paymentId()).isEqualTo(first.paymentId());
    }

    @Test
    void userId_ignored_sameDataForDifferentUsers() {
        var accountsA = platform.accountInformation("user-a").listAccounts();
        var accountsB = platform.accountInformation("user-b").listAccounts();
        assertThat(accountsA).isEqualTo(accountsB);
    }
}
