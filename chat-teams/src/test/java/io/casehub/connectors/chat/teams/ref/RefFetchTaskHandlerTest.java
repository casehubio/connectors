package io.casehub.connectors.chat.teams.ref;

import io.casehub.connectors.chat.teams.InvokeRequest;
import io.casehub.connectors.chat.teams.InvokeResponse;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RefFetchTaskHandlerTest {

    private final RefFetchTaskHandler handler = new RefFetchTaskHandler();

    @Test
    void name_returnsComposeExtensionFetchTask() {
        assertThat(handler.name()).isEqualTo("composeExtension/fetchTask");
    }

    @Test
    void handle_returnsComposeContinueWithTitle() {
        var request = new InvokeRequest("composeExtension/fetchTask",
                Json.createObjectBuilder().add("commandId", "createItem").build(),
                "user-1", "channel-1", Map.of());

        InvokeResponse response = handler.handle(request);

        assertThat(response).isInstanceOf(InvokeResponse.ComposeContinue.class);
        var result = (InvokeResponse.ComposeContinue) response;
        assertThat(result.title()).isEqualTo("Create Item");
    }

    @Test
    void handle_formCardIsValidAdaptiveCard() {
        var request = new InvokeRequest("composeExtension/fetchTask",
                Json.createObjectBuilder().add("commandId", "createItem").build(),
                "user-1", "channel-1", Map.of());

        var result = (InvokeResponse.ComposeContinue) handler.handle(request);
        JsonObject card = Json.createReader(new StringReader(result.cardJson())).readObject();

        assertThat(card.getString("type")).isEqualTo("AdaptiveCard");
        assertThat(card.getJsonArray("body")).isNotEmpty();
        assertThat(card.getJsonArray("actions")).isNotEmpty();
    }
}
