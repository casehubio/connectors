package io.casehub.connectors.email.google.inbound;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import io.casehub.connectors.HttpMethod;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GmailPushInboundConnectorTest {

    @Test
    void idMatchesConstant() {
        var connector = createConnector("");
        assertThat(connector.id()).isEqualTo(InboundConnectorIds.GMAIL_PUSH);
    }

    @Test
    void missingAuthorizationReturnsUnauthorized() {
        var connector = createConnector("test-audience");
        var request = new WebhookRequest("{}", Map.of(), Map.of(), HttpMethod.POST, "https://example.com/webhook");
        var result = connector.handle(request);
        assertThat(result).isInstanceOf(WebhookResult.Unauthorized.class);
    }

    @Test
    void invalidJwtReturnsUnauthorized() {
        var connector = createConnector("test-audience");
        var request = pubsubRequest("invalid-jwt", "user@example.com", 12345L);
        var result = connector.handle(request);
        assertThat(result).isInstanceOf(WebhookResult.Unauthorized.class);
    }

    @Test
    void validJwtReturnsDeferredDelivery() {
        var connector = createConnector("test-audience");
        String jwt = fakeJwt("test-audience", Instant.now().getEpochSecond() + 3600);
        var request = pubsubRequest(jwt, "user@example.com", 12345L);
        var result = connector.handle(request);
        assertThat(result).isInstanceOf(WebhookResult.DeferredDelivery.class);
    }

    @Test
    void blankAudienceSkipsValidation() {
        var connector = createConnector("");
        String jwt = "any.thing.here";
        var request = pubsubRequest(jwt, "user@example.com", 12345L);
        var result = connector.handle(request);
        assertThat(result).isInstanceOf(WebhookResult.DeferredDelivery.class);
    }

    @Test
    void malformedBodyReturnsIgnored() {
        var connector = createConnector("");
        String jwt = "any.thing.here";
        var request = new WebhookRequest("not-json",
                Map.of("authorization", List.of("Bearer " + jwt)),
                Map.of(), HttpMethod.POST, "https://example.com/webhook");
        var result = connector.handle(request);
        assertThat(result).isInstanceOf(WebhookResult.Ignored.class);
    }

    @Test
    void missingEmailAddressReturnsIgnored() {
        var connector = createConnector("");
        String jwt = "any.thing.here";
        var data = Base64.getEncoder().encodeToString("{\"historyId\":100}".getBytes());
        var body = "{\"message\":{\"data\":\"" + data + "\"}}";
        var request = new WebhookRequest(body,
                Map.of("authorization", List.of("Bearer " + jwt)),
                Map.of(), HttpMethod.POST, "https://example.com/webhook");
        var result = connector.handle(request);
        assertThat(result).isInstanceOf(WebhookResult.Ignored.class);
    }

    private GmailPushInboundConnector createConnector(String audience) {
        return new GmailPushInboundConnector(
                new InMemoryHistoryStore(), null, audience);
    }

    private static WebhookRequest pubsubRequest(String jwt, String email, long historyId) {
        var data = Base64.getEncoder().encodeToString(
                ("{\"emailAddress\":\"" + email + "\",\"historyId\":" + historyId + "}").getBytes());
        var body = "{\"message\":{\"data\":\"" + data + "\"}}";
        return new WebhookRequest(body,
                Map.of("authorization", List.of("Bearer " + jwt)),
                Map.of(), HttpMethod.POST, "https://example.com/webhook");
    }

    private static String fakeJwt(String audience, long exp) {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"RS256\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"accounts.google.com\",\"aud\":\"" + audience + "\",\"exp\":" + exp + "}").getBytes());
        return header + "." + payload + ".fake-signature";
    }
}
