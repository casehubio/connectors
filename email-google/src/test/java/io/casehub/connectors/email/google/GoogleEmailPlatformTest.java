package io.casehub.connectors.email.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import io.casehub.connectors.PageRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.NoSuchElementException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleEmailPlatformTest {

    private WireMockServer wireMock;
    private GoogleEmailPlatform platform;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        Gmail gmailService = new Gmail.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                request -> {})
                .setApplicationName("test")
                .setRootUrl("http://localhost:" + wireMock.port() + "/")
                .build();

        platform = new GoogleEmailPlatform(gmailService);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void id_isGoogle() {
        assertThat(platform.id()).isEqualTo("google");
    }

    @Test
    void requireClient_noClient_throwsIllegalState() {
        var unconfigured = new GoogleEmailPlatform((Gmail) null);
        assertThatThrownBy(unconfigured::listMailboxes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not initialised");
    }

    @Test
    void requiresScopes_annotationPresent() {
        var annotation = GoogleEmailPlatform.class.getAnnotation(
            io.casehub.platform.api.authn.RequiresScopes.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.provider()).isEqualTo("google");
    }

    @Test
    void listMailboxes_mapsGmailLabels() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/labels"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "labels": [
                                    {"id": "INBOX", "name": "INBOX", "type": "system", "messagesUnread": 3, "messagesTotal": 42},
                                    {"id": "SENT", "name": "SENT", "type": "system", "messagesUnread": 0, "messagesTotal": 15},
                                    {"id": "DRAFT", "name": "DRAFT", "type": "system", "messagesUnread": 0, "messagesTotal": 2},
                                    {"id": "TRASH", "name": "TRASH", "type": "system", "messagesUnread": 0, "messagesTotal": 5},
                                    {"id": "SPAM", "name": "SPAM", "type": "system", "messagesUnread": 0, "messagesTotal": 10},
                                    {"id": "CATEGORY_SOCIAL", "name": "CATEGORY_SOCIAL", "type": "system"},
                                    {"id": "Label_1", "name": "Projects", "type": "user", "messagesUnread": 1, "messagesTotal": 8}
                                  ]
                                }
                                """)));

        var mailboxes = platform.listMailboxes();

        assertThat(mailboxes).extracting("name")
                .contains("Inbox", "Sent", "Drafts", "Projects");
        assertThat(mailboxes).extracting("name")
                .doesNotContain("CATEGORY_SOCIAL", "TRASH", "SPAM");
        var inbox = mailboxes.stream().filter(m -> m.name().equals("Inbox")).findFirst().orElseThrow();
        assertThat(inbox.id()).isEqualTo("INBOX");
        assertThat(inbox.unreadCount()).isEqualTo(3);
    }

    @Test
    void listMessages_returnsSummariesWithHeaders() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "messages": [
                                    {"id": "msg-1", "threadId": "t1"},
                                    {"id": "msg-2", "threadId": "t2"}
                                  ],
                                  "resultSizeEstimate": 2
                                }
                                """)));

        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "msg-1",
                                  "labelIds": ["INBOX"],
                                  "payload": {
                                    "headers": [
                                      {"name": "From", "value": "alice@example.com"},
                                      {"name": "Subject", "value": "Hello"},
                                      {"name": "Date", "value": "Mon, 15 Sep 2026 09:30:00 +0000"},
                                      {"name": "Message-ID", "value": "<msg-1@mail.example.com>"}
                                    ]
                                  },
                                  "internalDate": "1789388200000"
                                }
                                """)));

        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-2"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "msg-2",
                                  "labelIds": ["INBOX", "UNREAD"],
                                  "payload": {
                                    "headers": [
                                      {"name": "From", "value": "bob@example.com"},
                                      {"name": "Subject", "value": "Review needed"},
                                      {"name": "Date", "value": "Tue, 16 Sep 2026 14:15:00 +0000"},
                                      {"name": "Message-ID", "value": "<msg-2@mail.example.com>"}
                                    ]
                                  },
                                  "internalDate": "1789474500000"
                                }
                                """)));

        var page = platform.listMessages("INBOX",
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-30T00:00:00Z"),
                PageRequest.first(10));

        assertThat(page.items()).hasSize(2);
        var first = page.items().getFirst();
        assertThat(first.id()).isEqualTo("msg-1");
        assertThat(first.from()).isEqualTo("alice@example.com");
        assertThat(first.subject()).isEqualTo("Hello");
        assertThat(first.read()).isTrue();
        assertThat(first.mailboxId()).isEqualTo("INBOX");

        var second = page.items().get(1);
        assertThat(second.read()).isFalse();
    }

    @Test
    void listMessages_pagination_withPageTokens() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages"))
                .withQueryParam("pageToken", WireMock.absent())
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "messages": [{"id": "msg-1", "threadId": "t1"}],
                                  "nextPageToken": "token-page2",
                                  "resultSizeEstimate": 2
                                }
                                """)));

        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "msg-1",
                                  "labelIds": ["INBOX"],
                                  "payload": {
                                    "headers": [
                                      {"name": "From", "value": "alice@example.com"},
                                      {"name": "Subject", "value": "Page 1"},
                                      {"name": "Date", "value": "Mon, 15 Sep 2026 09:30:00 +0000"}
                                    ]
                                  },
                                  "internalDate": "1789388200000"
                                }
                                """)));

        var firstPage = platform.listMessages("INBOX",
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-30T00:00:00Z"),
                PageRequest.first(1));

        assertThat(firstPage.items()).hasSize(1);
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isEqualTo("token-page2");
    }

    @Test
    void listMessages_emptyResult() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "resultSizeEstimate": 0
                                }
                                """)));

        var page = platform.listMessages("INBOX",
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-30T00:00:00Z"),
                PageRequest.first(10));

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void getMessage_returnsFullMessageWithParsedBody() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "msg-1",
                                  "labelIds": ["INBOX"],
                                  "payload": {
                                    "headers": [
                                      {"name": "From", "value": "alice@example.com"},
                                      {"name": "To", "value": "user@example.com, bob@example.com"},
                                      {"name": "Cc", "value": "carol@example.com"},
                                      {"name": "Subject", "value": "Test message"},
                                      {"name": "Date", "value": "Mon, 15 Sep 2026 09:30:00 +0000"},
                                      {"name": "Message-ID", "value": "<msg-1@mail.example.com>"}
                                    ],
                                    "mimeType": "multipart/alternative",
                                    "parts": [
                                      {
                                        "mimeType": "text/plain",
                                        "body": {"data": "%s"}
                                      },
                                      {
                                        "mimeType": "text/html",
                                        "body": {"data": "%s"}
                                      }
                                    ]
                                  },
                                  "internalDate": "1789388200000"
                                }
                                """.formatted(
                                base64url("Hello, this is a test."),
                                base64url("<html><body><p>Hello, this is a test.</p></body></html>")))));

        var message = platform.getMessage("INBOX", "msg-1");

        assertThat(message.id()).isEqualTo("msg-1");
        assertThat(message.mailboxId()).isEqualTo("INBOX");
        assertThat(message.messageId()).isEqualTo("<msg-1@mail.example.com>");
        assertThat(message.from()).isEqualTo("alice@example.com");
        assertThat(message.to()).containsExactly("user@example.com", "bob@example.com");
        assertThat(message.cc()).containsExactly("carol@example.com");
        assertThat(message.subject()).isEqualTo("Test message");
        assertThat(message.bodyText()).isEqualTo("Hello, this is a test.");
        assertThat(message.bodyHtml()).contains("<p>Hello, this is a test.</p>");
        assertThat(message.read()).isTrue();
    }

    @Test
    void getMessage_textOnlyBody() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-text"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "msg-text",
                                  "labelIds": ["INBOX"],
                                  "payload": {
                                    "headers": [
                                      {"name": "From", "value": "alice@example.com"},
                                      {"name": "To", "value": "user@example.com"},
                                      {"name": "Subject", "value": "Plain text"},
                                      {"name": "Date", "value": "Mon, 15 Sep 2026 09:30:00 +0000"}
                                    ],
                                    "mimeType": "text/plain",
                                    "body": {"data": "%s"}
                                  },
                                  "internalDate": "1789388200000"
                                }
                                """.formatted(base64url("Just plain text.")))));

        var message = platform.getMessage("INBOX", "msg-text");

        assertThat(message.bodyText()).isEqualTo("Just plain text.");
        assertThat(message.bodyHtml()).isNull();
    }

    @Test
    void getMessage_withAttachments() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-att"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "msg-att",
                                  "labelIds": ["INBOX"],
                                  "payload": {
                                    "headers": [
                                      {"name": "From", "value": "bob@example.com"},
                                      {"name": "To", "value": "user@example.com"},
                                      {"name": "Subject", "value": "With attachment"},
                                      {"name": "Date", "value": "Mon, 15 Sep 2026 09:30:00 +0000"}
                                    ],
                                    "mimeType": "multipart/mixed",
                                    "parts": [
                                      {
                                        "mimeType": "text/plain",
                                        "body": {"data": "%s"}
                                      },
                                      {
                                        "partId": "1",
                                        "mimeType": "application/pdf",
                                        "filename": "report.pdf",
                                        "body": {"attachmentId": "att-123", "size": 4096}
                                      }
                                    ]
                                  },
                                  "internalDate": "1789388200000"
                                }
                                """.formatted(base64url("See attached.")))));

        var message = platform.getMessage("INBOX", "msg-att");

        assertThat(message.attachments()).hasSize(1);
        var att = message.attachments().getFirst();
        assertThat(att.id()).isEqualTo("att-123");
        assertThat(att.filename()).isEqualTo("report.pdf");
        assertThat(att.contentType()).isEqualTo("application/pdf");
        assertThat(att.size()).isEqualTo(4096);
    }

    @Test
    void getMessage_unknownMessage_throws() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages/nonexistent"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error": {"code": 404, "message": "Not Found"}}
                                """)));

        assertThatThrownBy(() -> platform.getMessage("INBOX", "nonexistent"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getAttachmentContent_returnsDecodedBytes() {
        byte[] originalContent = "PDF content here".getBytes();
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(originalContent);

        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-1/attachments/att-123"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "attachmentId": "att-123",
                                  "size": %d,
                                  "data": "%s"
                                }
                                """.formatted(originalContent.length, encoded))));

        var content = platform.getAttachmentContent("INBOX", "msg-1", "att-123");

        assertThat(content).isEqualTo(originalContent);
    }

    @Test
    void getAttachmentContent_serverError_throws() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-1/attachments/att-bad"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() ->
                platform.getAttachmentContent("INBOX", "msg-1", "att-bad"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void search_passesQueryToGmailApi() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages"))
                                 .withQueryParam("q", WireMock.equalTo("invoice"))
                                 .willReturn(aResponse()
                                                     .withHeader("Content-Type", "application/json")
                                                     .withBody("""
                                                               {
                                                                 "messages": [
                                                                   {"id": "msg-s1", "threadId": "t1"}
                                                                 ],
                                                                 "resultSizeEstimate": 1
                                                               }
                                                               """)));

        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-s1"))
                                 .willReturn(aResponse()
                                                     .withHeader("Content-Type", "application/json")
                                                     .withBody("""
                                                               {
                                                                 "id": "msg-s1",
                                                                 "labelIds": ["INBOX"],
                                                                 "payload": {
                                                                   "headers": [
                                                                     {"name": "From", "value": "bob@example.com"},
                                                                     {"name": "Subject", "value": "Invoice #4821"},
                                                                     {"name": "Date", "value": "Mon, 15 Sep 2026 09:30:00 +0000"},
                                                                     {"name": "Message-ID", "value": "<msg-s1@mail.example.com>"}
                                                                   ]
                                                                 },
                                                                 "internalDate": "1789388200000"
                                                               }
                                                               """)));

        var page = platform.search("invoice", PageRequest.first(10));

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().subject()).isEqualTo("Invoice #4821");
        assertThat(page.items().getFirst().mailboxId()).isEqualTo("INBOX");
    }

    @Test
    void search_emptyResult() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages"))
                                 .willReturn(aResponse()
                                                     .withHeader("Content-Type", "application/json")
                                                     .withBody("""
                                                               {
                                                                 "resultSizeEstimate": 0
                                                               }
                                                               """)));

        var page = platform.search("nonexistent", PageRequest.first(10));

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }


    private static String base64url(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes());
    }
}
