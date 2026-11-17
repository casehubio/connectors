package io.casehub.connectors.chat.teams.ref;

import io.casehub.connectors.chat.teams.InvokeRequest;
import io.casehub.connectors.chat.teams.InvokeResponse;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RefLinkUnfurlHandlerTest {

    private final RefLinkUnfurlHandler handler = new RefLinkUnfurlHandler();

    @Test
    void name_returnsComposeExtensionQueryLink() {
        assertThat(handler.name()).isEqualTo("composeExtension/queryLink");
    }

    @Test
    void handle_returnsComposeResultWithOneCard() {
        var request = new InvokeRequest("composeExtension/queryLink",
                Json.createObjectBuilder().add("url", "https://example.com/item/42").build(),
                "user-1", "channel-1", Map.of());

        InvokeResponse response = handler.handle(request);

        assertThat(response).isInstanceOf(InvokeResponse.ComposeResult.class);
        var result = (InvokeResponse.ComposeResult) response;
        assertThat(result.attachmentLayout()).isEqualTo("list");
        assertThat(result.attachmentsJson()).hasSize(1);
    }

    @Test
    void handle_unfurlCardHasNoPreview() {
        var request = new InvokeRequest("composeExtension/queryLink",
                Json.createObjectBuilder().add("url", "https://example.com/item/42").build(),
                "user-1", "channel-1", Map.of());

        var result = (InvokeResponse.ComposeResult) handler.handle(request);
        JsonObject attachment = parse(result.attachmentsJson().get(0));

        assertThat(attachment.getString("contentType")).isEqualTo("application/vnd.microsoft.card.adaptive");
        assertThat(attachment.containsKey("preview")).isFalse();
    }

    private static JsonObject parse(String json) {
        return Json.createReader(new StringReader(json)).readObject();
    }
}
