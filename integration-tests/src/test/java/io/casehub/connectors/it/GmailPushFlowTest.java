package io.casehub.connectors.it;

import io.casehub.connectors.HttpMethod;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;
import io.casehub.connectors.email.google.inbound.GmailPushInboundConnector;
import io.casehub.connectors.email.google.inbound.InMemoryHistoryStore;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GmailPushFlowTest {

    private static final io.casehub.connectors.email.google.inbound.JwtValidator ACCEPT_ALL = jwt -> true;
    private static final io.casehub.connectors.email.google.inbound.JwtValidator REJECT_ALL = jwt -> false;

    @Test
    void fullPushNotificationFlow() {
        var store     = new InMemoryHistoryStore();
        var connector = new GmailPushInboundConnector(store, null, ACCEPT_ALL);

        assertThat(connector.id()).isEqualTo(InboundConnectorIds.GMAIL_PUSH);

        String jwt     = "any.thing.here";
        var    request = pubsubRequest(jwt, "user@example.com", 12345L);

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
    void rejectedJwtReturnsUnauthorized() {
        var connector = new GmailPushInboundConnector(
                new InMemoryHistoryStore(), null, REJECT_ALL);

        var request = pubsubRequest("any.jwt.here", "user@example.com", 100L);
        assertThat(connector.handle(request)).isInstanceOf(WebhookResult.Unauthorized.class);
    }

    @Test
    void acceptedJwtReturnsDeferredDelivery() {
        var connector = new GmailPushInboundConnector(
                new InMemoryHistoryStore(), null, ACCEPT_ALL);

        var request = pubsubRequest("any.jwt.here", "user@example.com", 100L);
        assertThat(connector.handle(request)).isInstanceOf(WebhookResult.DeferredDelivery.class);
    }

    @Test
    void malformedPubsubBodyReturnsIgnored() {
        var connector = new GmailPushInboundConnector(
                new InMemoryHistoryStore(), null, ACCEPT_ALL);

        var request = new WebhookRequest("{\"not\":\"pubsub\"}",
                                         Map.of("authorization", List.of("Bearer any.thing.here")),
                                         Map.of(), HttpMethod.POST, "https://example.com/webhook");

        assertThat(connector.handle(request)).isInstanceOf(WebhookResult.Ignored.class);
    }

    @Test
    void noAuthorizationHeaderReturnsUnauthorized() {
        var connector = new GmailPushInboundConnector(
                new InMemoryHistoryStore(), null, ACCEPT_ALL);

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
}
