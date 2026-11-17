package io.casehub.connectors.chat.teams.ref;

import io.casehub.connectors.chat.teams.InvokeRequest;
import io.casehub.connectors.chat.teams.InvokeResponse;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RefSearchHandlerTest {

    private final RefSearchHandler handler = new RefSearchHandler();

    @Test
    void name_returnsComposeExtensionQuery() {
        assertThat(handler.name()).isEqualTo("composeExtension/query");
    }

    @Test
    void handle_returnsComposeResultWithThreeResults() {
        var request = new InvokeRequest("composeExtension/query",
                                        Json.createObjectBuilder().add("queryText", "").build(),
                                        "user-1", "channel-1", Map.of());

        InvokeResponse response = handler.handle(request);

        assertThat(response).isInstanceOf(InvokeResponse.ComposeResult.class);
        var result = (InvokeResponse.ComposeResult) response;
        assertThat(result.attachmentLayout()).isEqualTo("list");
        assertThat(result.attachmentsJson()).hasSize(3);
    }

    @Test
    void handle_attachmentsHavePreviewCards() {
        var request = new InvokeRequest("composeExtension/query",
                                        Json.createObjectBuilder().add("queryText", "").build(),
                                        "user-1", "channel-1", Map.of());

        var        result = (InvokeResponse.ComposeResult) handler.handle(request);
        JsonObject first  = parse(result.attachmentsJson().get(0));

        assertThat(first.getString("contentType")).isEqualTo("application/vnd.microsoft.card.adaptive");
        assertThat(first.getJsonObject("content").getString("type")).isEqualTo("AdaptiveCard");
        assertThat(first.getJsonObject("preview")).isNotNull();
        assertThat(first.getJsonObject("preview").getJsonObject("content")
                        .getString("title")).isNotEmpty();
    }

    @Test
    void handle_blankQuery_returnsAllResults() {
        var request = new InvokeRequest("composeExtension/query",
                                        Json.createObjectBuilder().add("queryText", "").build(),
                                        "user-1", "channel-1", Map.of());

        var result = (InvokeResponse.ComposeResult) handler.handle(request);
        assertThat(result.attachmentsJson()).hasSize(3);
    }

    @Test
    void handle_missingQueryText_returnsAllResults() {
        var request = new InvokeRequest("composeExtension/query",
                                        Json.createObjectBuilder().build(),
                                        "user-1", "channel-1", Map.of());

        var result = (InvokeResponse.ComposeResult) handler.handle(request);
        assertThat(result.attachmentsJson()).hasSize(3);
    }

    @Test
    void handle_matchingQuery_filtersResults() {
        var request = new InvokeRequest("composeExtension/query",
                                        Json.createObjectBuilder().add("queryText", "widget").build(),
                                        "user-1", "channel-1", Map.of());

        var result = (InvokeResponse.ComposeResult) handler.handle(request);
        assertThat(result.attachmentsJson()).hasSize(1);

        JsonObject first = parse(result.attachmentsJson().get(0));
        assertThat(first.getJsonObject("preview").getJsonObject("content")
                        .getString("title")).isEqualTo("Contoso Widget");
    }

    @Test
    void handle_caseInsensitiveQuery_filtersResults() {
        var request = new InvokeRequest("composeExtension/query",
                                        Json.createObjectBuilder().add("queryText", "GADGET").build(),
                                        "user-1", "channel-1", Map.of());

        var result = (InvokeResponse.ComposeResult) handler.handle(request);
        assertThat(result.attachmentsJson()).hasSize(1);
    }

    @Test
    void handle_noMatchQuery_returnsEmptyList() {
        var request = new InvokeRequest("composeExtension/query",
                                        Json.createObjectBuilder().add("queryText", "nonexistent").build(),
                                        "user-1", "channel-1", Map.of());

        var result = (InvokeResponse.ComposeResult) handler.handle(request);
        assertThat(result.attachmentsJson()).isEmpty();
    }


    private static JsonObject parse(String json) {
        return Json.createReader(new StringReader(json)).readObject();
    }
}
