package io.casehub.connectors.it;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import io.casehub.connectors.HttpMethod;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;
import io.casehub.connectors.email.google.inbound.GmailPushInboundConnector;
import io.casehub.connectors.email.google.inbound.InMemoryHistoryStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GmailPushFlowTest {

    @Test
    void fullPushNotificationFlow() {
        var store = new InMemoryHistoryStore();
        var connector = new GmailPushInboundConnector(store, null, "");

        assertThat(connector.id()).isEqualTo(InboundConnectorIds.GMAIL_PUSH);

        String jwt = "any.thing.here";
        var request = pubsubRequest(jwt, "user@example.com", 12345L);

        var result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.DeferredDelivery.class);
        var deferred = (WebhookResult.DeferredDelivery) result;
        assertThat(deferred.immediate()).isEmpty();
    }

    @Test
    void historyStoreTracksPerUser() {
        var store = new InMemoryHistoryStore();

        assertThat(store.getLastHistoryId("alice@example.com")).isEmpty();
        assertThat(store.getLastHistoryId("bob@example.com")).isEmpty();

        store.setLastHistoryId("alice@example.com", 100L);
        store.setLastHistoryId("bob@example.com", 200L);

        assertThat(store.getLastHistoryId("alice@example.com")).hasValue(100L);
        assertThat(store.getLastHistoryId("bob@example.com")).hasValue(200L);

        store.setLastHistoryId("alice@example.com", 150L);
        assertThat(store.getLastHistoryId("alice@example.com")).hasValue(150L);
    }

    @Test
    void jwtValidationRejectsExpiredToken() {
        var connector = new GmailPushInboundConnector(
                new InMemoryHistoryStore(), null, "my-audience");

        String expired = fakeJwt("my-audience", "accounts.google.com",
                Instant.now().getEpochSecond() - 300);
        var request = pubsubRequest(expired, "user@example.com", 100L);

        assertThat(connector.handle(request)).isInstanceOf(WebhookResult.Unauthorized.class);
    }

    @Test
    void jwtValidationRejectsWrongAudience() {
        var connector = new GmailPushInboundConnector(
                new InMemoryHistoryStore(), null, "expected-audience");

        String wrong = fakeJwt("wrong-audience", "accounts.google.com",
                Instant.now().getEpochSecond() + 3600);
        var request = pubsubRequest(wrong, "user@example.com", 100L);

        assertThat(connector.handle(request)).isInstanceOf(WebhookResult.Unauthorized.class);
    }

    @Test
    void jwtValidationAcceptsValidClaims() {
        var connector = new GmailPushInboundConnector(
                new InMemoryHistoryStore(), null, "my-audience");

        String valid = fakeJwt("my-audience", "accounts.google.com",
                Instant.now().getEpochSecond() + 3600);
        var request = pubsubRequest(valid, "user@example.com", 100L);

        assertThat(connector.handle(request)).isInstanceOf(WebhookResult.DeferredDelivery.class);
    }

    @Test
    void jwtValidationAcceptsHttpsIssuer() {
        var connector = new GmailPushInboundConnector(
                new InMemoryHistoryStore(), null, "my-audience");

        String valid = fakeJwt("my-audience", "https://accounts.google.com",
                Instant.now().getEpochSecond() + 3600);
        var request = pubsubRequest(valid, "user@example.com", 100L);

        assertThat(connector.handle(request)).isInstanceOf(WebhookResult.DeferredDelivery.class);
    }

    @Test
    void malformedPubsubBodyReturnsIgnored() {
        var connector = new GmailPushInboundConnector(
                new InMemoryHistoryStore(), null, "");

        var request = new WebhookRequest("{\"not\":\"pubsub\"}",
                Map.of("authorization", List.of("Bearer any.thing.here")),
                Map.of(), HttpMethod.POST, "https://example.com/webhook");

        assertThat(connector.handle(request)).isInstanceOf(WebhookResult.Ignored.class);
    }

    @Test
    void noAuthorizationHeaderReturnsUnauthorized() {
        var connector = new GmailPushInboundConnector(
                new InMemoryHistoryStore(), null, "audience");

        var request = new WebhookRequest("{}",
                Map.of(), Map.of(), HttpMethod.POST, "https://example.com/webhook");

        assertThat(connector.handle(request)).isInstanceOf(WebhookResult.Unauthorized.class);
    }

    private static WebhookRequest pubsubRequest(String jwt, String email, long historyId) {
        var data = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"emailAddress\":\"" + email + "\",\"historyId\":" + historyId + "}").getBytes());
        var body = "{\"message\":{\"data\":\"" + data + "\"}}";
        return new WebhookRequest(body,
                Map.of("authorization", List.of("Bearer " + jwt)),
                Map.of(), HttpMethod.POST, "https://example.com/webhook");
    }

    private static String fakeJwt(String audience, String issuer, long exp) {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"RS256\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"" + issuer + "\",\"aud\":\"" + audience + "\",\"exp\":" + exp + "}").getBytes());
        return header + "." + payload + ".fake-signature";
    }
}
