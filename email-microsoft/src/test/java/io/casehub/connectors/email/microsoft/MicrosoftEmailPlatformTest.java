package io.casehub.connectors.email.microsoft;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.microsoft.MicrosoftGraphClient;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MicrosoftEmailPlatformTest {

    private MicrosoftEmailPlatform platform;

    @BeforeEach
    void setUp() {
        var graph = graphClient();
        platform = new MicrosoftEmailPlatform(new MicrosoftMailClient(graph));
    }

    @Test
    void idReturnsMicrosoft() {
        assertThat(platform.id()).isEqualTo("microsoft");
    }

    @Test
    void listMailboxesReturnsMappedFolders() {
        var mailboxes = platform.listMailboxes();
        assertThat(mailboxes).hasSize(2);
        assertThat(mailboxes.get(0).name()).isEqualTo("Inbox");
        assertThat(mailboxes.get(0).unreadCount()).isEqualTo(3);
        assertThat(mailboxes.get(1).name()).isEqualTo("Sent Items");
    }

    @Test
    void listMessagesReturnsSummaries() {
        var page = platform.listMessages("inbox-id",
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-14T00:00:00Z"),
                PageRequest.first(10));
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).from()).isEqualTo("alice@example.com");
        assertThat(page.items().get(0).mailboxId()).isEqualTo("inbox-id");
    }

    @Test
    void getMessageReturnsFullMessage() {
        var msg = platform.getMessage("inbox-id", "msg-1");
        assertThat(msg.id()).isEqualTo("msg-1");
        assertThat(msg.mailboxId()).isEqualTo("inbox-id");
        assertThat(msg.from()).isEqualTo("alice@example.com");
        assertThat(msg.subject()).isEqualTo("Hello");
    }

    @Test
    void searchReturnsSummaries() {
        var page = platform.search("test", PageRequest.first(10));
        assertThat(page.items()).hasSize(1);
    }

    private static MicrosoftGraphClient graphClient() {
        return new MicrosoftGraphClient(request -> {
            String uri = request.uri().toString();
            if (uri.contains("/me/mailFolders") && !uri.contains("/messages")) {
                return stubResponse(200, """
                        {"value":[
                            {"id":"inbox-id","displayName":"Inbox","unreadItemCount":3},
                            {"id":"sent-id","displayName":"Sent Items","unreadItemCount":0}
                        ]}""");
            }
            if (uri.contains("/messages") && uri.contains("$expand=attachments")) {
                return stubResponse(200, messageJsonString("msg-1",
                        "alice@example.com", "Hello"));
            }
            if (uri.contains("/messages")) {
                return stubResponse(200, """
                        {"value":[%s]}""".formatted(
                                messageJsonString("msg-1", "alice@example.com", "Hello")));
            }
            return stubResponse(200, """
                    {"value":[]}""");
        });
    }

    private static String messageJsonString(String id, String from, String subject) {
        return """
                {"id":"%s","subject":"%s","receivedDateTime":"2026-11-13T10:00:00Z","isRead":false,"from":{"emailAddress":{"address":"%s"}},"body":{"contentType":"text","content":"body text"}}"""
                .formatted(id, subject, from);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> stubResponse(int status, String body) {
        return new StubHttpResponse(status, body);
    }
}
