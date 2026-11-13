package io.casehub.connectors.microsoft;

import jakarta.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MicrosoftIntegrationTest {

    private MicrosoftGraphClient graph;

    @BeforeEach
    void setUp() {
        graph = new MicrosoftGraphClient(this::routeRequest);
    }

    @Test
    void graphClientGetParsesJson() {
        var result = graph.get("/me/mailFolders", "token");
        assertThat(result).isNotNull();
        assertThat(result.getJsonArray("value")).hasSize(1);
    }

    @Test
    void graphClientPostParsesJson() {
        var body   = Json.createObjectBuilder().add("test", true).build();
        var result = graph.post("/teams/t1/channels/ch1/messages", body, "token");
        assertThat(result).isNotNull();
        assertThat(result.getString("id")).isEqualTo("msg-1");
    }

    @Test
    void graphClientGetPageHandlesODataPagination() {
        var page = graph.getPage("/me/calendars", "token",
                                 json -> json.getString("id"), null);
        assertThat(page.items()).containsExactly("cal-1");
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void graphClientGetPageHandlesDeltaLink() {
        var page = graph.getPage("/me/drive/root/delta", "token",
                                 Function.identity(), null);
        assertThat(page.items()).hasSize(1);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).contains("deltaToken");
    }

    @Test
    void graphClientSendRoutesCustomRequests() {
        var request = HttpRequest.newBuilder(
                                         URI.create(MicrosoftGraphClient.BASE_URL + "/me/drive/items/root:/test.txt:/content"))
                                 .header("Authorization", "Bearer token")
                                 .header("Content-Type", "text/plain")
                                 .PUT(HttpRequest.BodyPublishers.ofByteArray("hello".getBytes()))
                                 .build();
        var response = graph.send(request);
        assertThat(response.statusCode()).isEqualTo(201);
    }

    @Test
    void graphClientDeleteSucceeds() {
        assertThatCode(() -> graph.delete("/me/events/evt-1", "token"))
                .doesNotThrowAnyException();
    }

    @Test
    void graphClientPatchParsesJson() {
        var body   = Json.createObjectBuilder().add("name", "moved").build();
        var result = graph.patch("/me/drive/items/file-1", body, "token");
        assertThat(result).isNotNull();
    }

    @Test
    void baseUrlIsPublic() {
        assertThat(MicrosoftGraphClient.BASE_URL)
                .isEqualTo("https://graph.microsoft.com/v1.0");
    }

    @Test
    void allBeanClassesAccessible() {
        assertThat(io.casehub.connectors.document.microsoft.DocumentMicrosoftBeans.class).isNotNull();
        assertThat(io.casehub.connectors.calendar.microsoft.CalendarMicrosoftBeans.class).isNotNull();
        assertThat(io.casehub.connectors.chat.teams.TeamsBeans.class).isNotNull();
        assertThat(io.casehub.connectors.document.microsoft.MicrosoftDrivePlatform.class).isNotNull();
        assertThat(io.casehub.connectors.calendar.microsoft.MicrosoftCalendarPlatform.class).isNotNull();
        assertThat(io.casehub.connectors.email.microsoft.MicrosoftEmailPlatform.class).isNotNull();
        assertThat(io.casehub.connectors.chat.teams.TeamsChatPlatform.class).isNotNull();
    }

    private HttpResponse<String> routeRequest(HttpRequest request) {
        String uri    = request.uri().toString();
        String method = request.method();

        if (uri.contains("/mailFolders")) {
            return stub(200, """
                             {"value":[{"id":"inbox","displayName":"Inbox"}]}""");
        }
        if (uri.contains("/me/calendars")) {
            return stub(200, """
                             {"value":[{"id":"cal-1","name":"Calendar"}]}""");
        }
        if (uri.contains("/delta")) {
            return stub(200, """
                             {"value":[{"id":"file-1","name":"doc.pdf"}],\
                             "@odata.deltaLink":"https://graph.microsoft.com/v1.0/delta?deltaToken=xyz"}""");
        }
        if (uri.contains("/messages") && method.equals("POST")) {
            return stub(201, """
                             {"id":"msg-1","createdDateTime":"2026-11-13T10:00:00Z"}""");
        }
        if (uri.contains(":/content") && method.equals("PUT")) {
            return stub(201, """
                             {"id":"file-new","name":"test.txt"}""");
        }
        if (method.equals("DELETE")) {
            return stub(204, "");
        }
        if (method.equals("PATCH")) {
            return stub(200, """
                             {"id":"updated"}""");
        }
        return stub(200, """
                         {"value":[]}""");
    }

    private static StubHttpResponse stub(int status, String body) {
        return new StubHttpResponse(status, body);
    }
}
