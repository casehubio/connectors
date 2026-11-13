package io.casehub.connectors.microsoft;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.*;

class MicrosoftGraphClientTest {

    @Test
    void getReturnsJsonBody() {
        var client = clientReturning(200, """
                {"id":"123","displayName":"Test"}""");
        var result = client.get("/me/profile", "token");
        assertThat(result.getString("id")).isEqualTo("123");
        assertThat(result.getString("displayName")).isEqualTo("Test");
    }

    @Test
    void getThrowsOnErrorStatus() {
        var client = clientReturning(404, """
                {"error":{"code":"ErrorItemNotFound","message":"not found"}}""");
        assertThatThrownBy(() -> client.get("/me/messages/invalid", "token"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void postSendsJsonBody() {
        var client = new MicrosoftGraphClient(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.uri().toString())
                    .isEqualTo(MicrosoftGraphClient.BASE_URL + "/me/events");
            return new StubHttpResponse(201, """
                    {"id":"new-1"}""");
        });
        var body = Json.createObjectBuilder().add("subject", "test").build();
        var result = client.post("/me/events", body, "token");
        assertThat(result.getString("id")).isEqualTo("new-1");
    }

    @Test
    void patchSendsJsonBody() {
        var client = new MicrosoftGraphClient(request -> {
            assertThat(request.method()).isEqualTo("PATCH");
            return new StubHttpResponse(200, """
                    {"id":"updated-1"}""");
        });
        var body = Json.createObjectBuilder().add("subject", "updated").build();
        var result = client.patch("/me/events/1", body, "token");
        assertThat(result.getString("id")).isEqualTo("updated-1");
    }

    @Test
    void deleteReturnsVoidOn204() {
        var client = clientReturning(204, "");
        assertThatCode(() -> client.delete("/me/events/123", "token"))
                .doesNotThrowAnyException();
    }

    @Test
    void deleteThrowsOnError() {
        var client = clientReturning(404, """
                {"error":{"code":"ErrorItemNotFound","message":"not found"}}""");
        assertThatThrownBy(() -> client.delete("/me/events/bad", "token"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void setsAuthorizationBearerHeader() {
        var client = new MicrosoftGraphClient(request -> {
            assertThat(request.headers().firstValue("Authorization"))
                    .hasValue("Bearer my-token");
            return new StubHttpResponse(200, "{}");
        });
        client.get("/me/profile", "my-token");
    }

    @Test
    void setsAcceptJsonHeader() {
        var client = new MicrosoftGraphClient(request -> {
            assertThat(request.headers().firstValue("Accept"))
                    .hasValue("application/json");
            return new StubHttpResponse(200, "{}");
        });
        client.get("/me/profile", "token");
    }

    @Test
    void getPageReturnsSinglePage() {
        var client = clientReturning(200, """
                {"value":[{"id":"1"},{"id":"2"}]}""");
        var page = client.getPage("/me/messages", "token",
                json -> json.getString("id"), null);
        assertThat(page.items()).containsExactly("1", "2");
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void getPageReturnsNextCursorFromOdataNextLink() {
        var client = clientReturning(200, """
                {"value":[{"id":"1"}],"@odata.nextLink":"https://graph.microsoft.com/v1.0/me/messages?$skip=10"}""");
        var page = client.getPage("/me/messages", "token",
                json -> json.getString("id"), null);
        assertThat(page.items()).containsExactly("1");
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor())
                .isEqualTo("https://graph.microsoft.com/v1.0/me/messages?$skip=10");
    }

    @Test
    void getPageUsesCursorAsFullUrl() {
        var cursorUrl = "https://graph.microsoft.com/v1.0/me/messages?$skip=10";
        var client = new MicrosoftGraphClient(request -> {
            assertThat(request.uri().toString()).isEqualTo(cursorUrl);
            return new StubHttpResponse(200, """
                    {"value":[{"id":"3"}]}""");
        });
        var page = client.getPage("/ignored", "token",
                json -> json.getString("id"), cursorUrl);
        assertThat(page.items()).containsExactly("3");
    }

    @Test
    void getPageHandlesEmptyValueArray() {
        var client = clientReturning(200, """
                {"value":[]}""");
        var page = client.getPage("/me/messages", "token",
                json -> json.getString("id"), null);
        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void getPageThrowsOnError() {
        var client = clientReturning(401, """
                {"error":{"code":"InvalidAuthenticationToken","message":"expired"}}""");
        assertThatThrownBy(() -> client.getPage("/me/messages", "token",
                json -> json.getString("id"), null))
                .isInstanceOf(io.casehub.connectors.ProviderAuthException.class);
    }

    private static MicrosoftGraphClient clientReturning(int status, String body) {
        return new MicrosoftGraphClient(request -> new StubHttpResponse(status, body));
    }
}
