package io.casehub.connectors.webhook;

import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.WebhookInboundConnector;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class RespondedTestConnector extends WebhookInboundConnector {

    @Override
    public String id() {
        return "responded-test";
    }

    @Override
    public WebhookResult handle(WebhookRequest request) {
        var msg = new InboundMessage(
                "responded-test", "test",
                "user1", "channel1", "invoke-event",
                List.of(), Instant.now(), Map.of(), null);
        return new WebhookResult.Responded(
                "{\"statusCode\":200}", "application/json",
                List.of(msg));
    }
}
