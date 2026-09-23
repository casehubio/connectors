package io.casehub.connectors.bank.truelayer;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.casehub.connectors.bank.truelayer.dto.TrueLayerAccount;
import io.casehub.connectors.bank.truelayer.dto.TrueLayerBalance;
import io.casehub.connectors.bank.truelayer.dto.TrueLayerPaymentResult;
import io.casehub.connectors.bank.truelayer.dto.TrueLayerPaymentStatus;
import io.casehub.connectors.bank.truelayer.dto.TrueLayerTransactionPage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrueLayerClientTest {

    static WireMockServer wireMock;
    static TrueLayerClient client;

    @BeforeAll
    static void setUp() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        client = new TrueLayerClient(null, "http://localhost:" + wireMock.port());
    }

    @AfterAll
    static void tearDown() {
        wireMock.stop();
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
    }

    @Test
    void listAccounts_returnsAccounts() {
        wireMock.stubFor(get(urlPathEqualTo("/data/v1/accounts"))
                .withHeader("Authorization", equalTo("Bearer user-token"))
                .willReturn(okJson("""
                    {"results": [
                        {"account_id": "acc-001", "display_name": "Current Account",
                         "account_type": "TRANSACTION", "currency": "GBP"},
                        {"account_id": "acc-002", "display_name": "Savings",
                         "account_type": "SAVINGS", "currency": "GBP"}
                    ]}
                    """)));

        var accounts = client.listAccounts("user-token");
        assertThat(accounts).hasSize(2);
        assertThat(accounts.get(0).accountId()).isEqualTo("acc-001");
        assertThat(accounts.get(0).displayName()).isEqualTo("Current Account");
        assertThat(accounts.get(1).accountId()).isEqualTo("acc-002");
    }

    @Test
    void balance_returnsBalance() {
        wireMock.stubFor(get(urlPathEqualTo("/data/v1/accounts/acc-001/balance"))
                .willReturn(okJson("""
                    {"results": [
                        {"available": 1250.00, "current": 1300.50,
                         "currency": "GBP", "update_timestamp": "2026-09-22T10:00:00Z"}
                    ]}
                    """)));

        var balance = client.balance("user-token", "acc-001");
        assertThat(balance.accountId()).isEqualTo("acc-001");
        assertThat(balance.available()).isEqualByComparingTo("1250.00");
        assertThat(balance.current()).isEqualByComparingTo("1300.50");
        assertThat(balance.currency()).isEqualTo("GBP");
    }

    @Test
    void listTransactions_returnsPage() {
        wireMock.stubFor(get(urlPathEqualTo("/data/v1/accounts/acc-001/transactions"))
                .willReturn(okJson("""
                    {"results": [
                        {"transaction_id": "tx-001", "amount": -15.99,
                         "currency": "GBP", "transaction_type": "DEBIT",
                         "description": "Coffee shop", "merchant_name": "Costa",
                         "transaction_category": "FOOD", "timestamp": "2026-09-20",
                         "status": "Booked"}
                    ], "next_cursor": "cursor-2", "has_more": true}
                    """)));

        var page = client.listTransactions("user-token", "acc-001",
                "2026-09-01", "2026-09-22", null);
        assertThat(page.results()).hasSize(1);
        assertThat(page.results().get(0).transactionId()).isEqualTo("tx-001");
        assertThat(page.nextCursor()).isEqualTo("cursor-2");
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    void error401_throwsConsentExpired() {
        wireMock.stubFor(get(urlPathEqualTo("/data/v1/accounts"))
                .willReturn(unauthorized()));

        assertThatThrownBy(() -> client.listAccounts("expired-token"))
                .isInstanceOf(ConsentExpiredException.class);
    }

    @Test
    void error404_throwsNoSuchElement() {
        wireMock.stubFor(get(urlPathEqualTo("/data/v1/accounts/bad-id/balance"))
                .willReturn(notFound().withBody("Not found")));

        assertThatThrownBy(() -> client.balance("user-token", "bad-id"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void error429_throwsRateLimited() {
        wireMock.stubFor(get(urlPathEqualTo("/data/v1/accounts"))
                .willReturn(aResponse().withStatus(429)
                        .withHeader("Retry-After", "30")));

        assertThatThrownBy(() -> client.listAccounts("user-token"))
                .isInstanceOf(RateLimitedException.class)
                .satisfies(e -> assertThat(((RateLimitedException) e).retryAfterSeconds()).isEqualTo(30));
    }

    @Test
    void error500_throwsTrueLayerApiException() {
        wireMock.stubFor(get(urlPathEqualTo("/data/v1/accounts"))
                .willReturn(serverError().withBody("Internal error")));

        assertThatThrownBy(() -> client.listAccounts("user-token"))
                .isInstanceOf(TrueLayerApiException.class)
                .satisfies(e -> assertThat(((TrueLayerApiException) e).statusCode()).isEqualTo(500));
    }
}
