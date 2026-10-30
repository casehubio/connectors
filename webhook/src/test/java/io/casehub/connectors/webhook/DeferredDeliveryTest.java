package io.casehub.connectors.webhook;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.WebhookResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeferredDeliveryTest {

    @Test
    void deferredDeliveryIsSealed() {
        var immediate = List.of(testMessage("text"));
        var deferred = new WebhookResult.DeferredDelivery(
                immediate,
                () -> List.of(testMessage("media")));
        assertThat(deferred).isInstanceOf(WebhookResult.class);
        assertThat(deferred.immediate()).hasSize(1);
    }

    @Test
    void deferredSupplierProducesMessages() {
        var deferred = new WebhookResult.DeferredDelivery(
                List.of(),
                () -> List.of(testMessage("deferred-content")));
        var deferredMessages = deferred.deferred().get();
        assertThat(deferredMessages).hasSize(1);
        assertThat(deferredMessages.get(0).content()).isEqualTo("deferred-content");
    }

    private static InboundMessage testMessage(String content) {
        return new InboundMessage("test", "test", "sender", "channel",
                content, List.of(), Instant.now(), Map.of(), null);
    }
}
