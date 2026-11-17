package io.casehub.connectors.chat.teams.ref;

import io.casehub.connectors.chat.teams.InvokeRequest;
import io.casehub.connectors.chat.teams.InvokeResponse;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RefSubmitActionHandlerTest {

    private final RefSubmitActionHandler handler = new RefSubmitActionHandler();

    @Test
    void name_returnsComposeExtensionSubmitAction() {
        assertThat(handler.name()).isEqualTo("composeExtension/submitAction");
    }

    @Test
    void handle_returnsComposeResultWithConfirmation() {
        var request = new InvokeRequest("composeExtension/submitAction",
                Json.createObjectBuilder().add("title", "Bug report").build(),
                "user-1", "channel-1", Map.of());

        InvokeResponse response = handler.handle(request);

        assertThat(response).isInstanceOf(InvokeResponse.ComposeResult.class);
        var result = (InvokeResponse.ComposeResult) response;
        assertThat(result.attachmentLayout()).isEqualTo("list");
        assertThat(result.attachmentsJson()).hasSize(1);
    }

    @Test
    void handle_confirmationHasPreview() {
        var request = new InvokeRequest("composeExtension/submitAction",
                Json.createObjectBuilder().add("title", "Bug report").build(),
                "user-1", "channel-1", Map.of());

        var result = (InvokeResponse.ComposeResult) handler.handle(request);
        JsonObject attachment = parse(result.attachmentsJson().get(0));

        assertThat(attachment.getString("contentType")).isEqualTo("application/vnd.microsoft.card.adaptive");
        assertThat(attachment.getJsonObject("preview")).isNotNull();
        assertThat(attachment.getJsonObject("preview").getJsonObject("content")
                .getString("title")).isEqualTo("Item Created");
    }

    private static JsonObject parse(String json) {
        return Json.createReader(new StringReader(json)).readObject();
    }
}
