package io.casehub.connectors.bank.truelayer;

import io.casehub.connectors.bank.model.PaymentStatus;
import io.casehub.connectors.bank.model.PaymentStatusChanged;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@TestProfile(TrueLayerTestProfile.class)
class TrueLayerPaymentWebhookTest {

    @Inject PaymentEventCapture capture;
    @Inject WebhookEventDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        capture.clear();
    }

    @Test
    void validWebhook_firesEvent() {
        given()
            .contentType("application/json")
            .body("""
                {"type":"payment_executed","event_id":"evt-001",
                 "event_version":1,"payment_id":"pay-123",
                 "payment_status":"executed",
                 "timestamp":"2026-09-25T10:30:00Z"}""")
            .when().post("/webhooks/truelayer/payments")
            .then().statusCode(200);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(capture.events()).hasSize(1);
            PaymentStatusChanged event = capture.events().getFirst();
            assertThat(event.platformId()).isEqualTo("truelayer");
            assertThat(event.paymentId()).isEqualTo("pay-123");
            assertThat(event.status()).isEqualTo(PaymentStatus.EXECUTED);
            assertThat(event.eventId()).isEqualTo("evt-001");
        });
    }

    @Test
    void malformedBody_returns400() {
        given()
            .contentType("application/json")
            .body("{\"type\":\"payment_executed\"}")
            .when().post("/webhooks/truelayer/payments")
            .then().statusCode(400);

        assertThat(capture.events()).isEmpty();
    }

    @Test
    void unknownEventType_returns200_noEvent() {
        given()
            .contentType("application/json")
            .body("""
                {"type":"payment_refunded","event_id":"evt-002",
                 "event_version":1,"payment_id":"pay-456",
                 "payment_status":"refunded",
                 "timestamp":"2026-09-25T11:00:00Z"}""")
            .when().post("/webhooks/truelayer/payments")
            .then().statusCode(200);

        assertThat(capture.events()).isEmpty();
    }

    @Test
    void duplicateEventId_returns200_noSecondEvent() {
        String body = """
            {"type":"payment_settled","event_id":"evt-dup",
             "event_version":1,"payment_id":"pay-789",
             "payment_status":"settled",
             "timestamp":"2026-09-25T12:00:00Z"}""";

        given().contentType("application/json").body(body)
            .when().post("/webhooks/truelayer/payments")
            .then().statusCode(200);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            assertThat(capture.events()).hasSize(1));

        given().contentType("application/json").body(body)
            .when().post("/webhooks/truelayer/payments")
            .then().statusCode(200);

        assertThat(capture.events()).hasSize(1);
    }

    @Test
    void failedPayment_includesFailureReason() {
        given()
            .contentType("application/json")
            .body("""
                {"type":"payment_failed","event_id":"evt-003",
                 "event_version":1,"payment_id":"pay-fail",
                 "payment_status":"failed",
                 "failure_reason":"insufficient_funds",
                 "timestamp":"2026-09-25T13:00:00Z"}""")
            .when().post("/webhooks/truelayer/payments")
            .then().statusCode(200);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(capture.events()).hasSize(1);
            PaymentStatusChanged event = capture.events().getFirst();
            assertThat(event.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(event.failureReason()).isEqualTo("insufficient_funds");
        });
    }

    @Test
    void allStatusValues_mappedCorrectly() {
        var mappings = List.of(
            new String[]{"payment_authorization_required", "AUTHORIZATION_REQUIRED"},
            new String[]{"payment_authorizing", "AUTHORIZING"},
            new String[]{"payment_authorized", "AUTHORIZED"},
            new String[]{"payment_executed", "EXECUTED"},
            new String[]{"payment_settled", "SETTLED"},
            new String[]{"payment_failed", "FAILED"});

        for (int i = 0; i < mappings.size(); i++) {
            capture.clear();
            String eventType = mappings.get(i)[0];
            PaymentStatus expected = PaymentStatus.valueOf(mappings.get(i)[1]);

            given()
                .contentType("application/json")
                .body("""
                    {"type":"%s","event_id":"evt-map-%d",
                     "event_version":1,"payment_id":"pay-map-%d",
                     "payment_status":"%s",
                     "timestamp":"2026-09-25T14:00:00Z"}"""
                    .formatted(eventType, i, i,
                        eventType.replaceFirst("^payment_", "")))
                .when().post("/webhooks/truelayer/payments")
                .then().statusCode(200);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(capture.events()).hasSize(1);
                assertThat(capture.events().getFirst().status()).isEqualTo(expected);
            });
        }
    }

    @ApplicationScoped
    public static class PaymentEventCapture {
        private final List<PaymentStatusChanged> events = new CopyOnWriteArrayList<>();

        void onEvent(@ObservesAsync PaymentStatusChanged event) {
            events.add(event);
        }

        public List<PaymentStatusChanged> events() { return events; }
        public void clear() { events.clear(); }
    }
}
