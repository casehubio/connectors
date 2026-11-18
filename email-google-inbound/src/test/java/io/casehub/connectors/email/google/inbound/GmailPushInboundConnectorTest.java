package io.casehub.connectors.email.google.inbound;

import io.casehub.connectors.HttpMethod;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GmailPushInboundConnectorTest {

    private static final JwtValidator ACCEPT_ALL = jwt -> true;
    private static final JwtValidator REJECT_ALL = jwt -> false;

    @Test
    void idMatchesConstant() {
        var connector = createConnector(ACCEPT_ALL);
        assertThat(connector.id()).isEqualTo(InboundConnectorIds.GMAIL_PUSH);
    }

    @Test
    void missingAuthorizationReturnsUnauthorized() {
        var connector = createConnector(ACCEPT_ALL);
        var request   = new WebhookRequest("{}", Map.of(), Map.of(), HttpMethod.POST, "https://example.com/webhook");
        var result    = connector.handle(request);
        assertThat(result).isInstanceOf(WebhookResult.Unauthorized.class);
    }

    @Test
    void rejectedJwtReturnsUnauthorized() {
        var connector = createConnector(REJECT_ALL);
        var request   = pubsubRequest("any.jwt.here", "user@example.com", 12345L);
        var result    = connector.handle(request);
        assertThat(result).isInstanceOf(WebhookResult.Unauthorized.class);
    }

    @Test
    void acceptedJwtReturnsDeferredDelivery() {
        var connector = createConnector(ACCEPT_ALL);
        var request   = pubsubRequest("valid.jwt.token", "user@example.com", 12345L);
        var result    = connector.handle(request);
        assertThat(result).isInstanceOf(WebhookResult.DeferredDelivery.class);
    }

    @Test
    void malformedBodyReturnsIgnored() {
        var connector = createConnector(ACCEPT_ALL);
        var request = new WebhookRequest("not-json",
                                         Map.of("authorization", List.of("Bearer valid.jwt.token")),
                                         Map.of(), HttpMethod.POST, "https://example.com/webhook");
        var result = connector.handle(request);
        assertThat(result).isInstanceOf(WebhookResult.Ignored.class);
    }

    @Test
    void missingEmailAddressReturnsIgnored() {
        var connector = createConnector(ACCEPT_ALL);
        var data      = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"historyId\":100}".getBytes());
        var body      = "{\"message\":{\"data\":\"" + data + "\"}}";
        var request = new WebhookRequest(body,
                                         Map.of("authorization", List.of("Bearer valid.jwt.token")),
                                         Map.of(), HttpMethod.POST, "https://example.com/webhook");
        var result = connector.handle(request);
        assertThat(result).isInstanceOf(WebhookResult.Ignored.class);
    }

    private GmailPushInboundConnector createConnector(JwtValidator validator) {
        return new GmailPushInboundConnector(
                new InMemoryHistoryStore(), null, validator);
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
