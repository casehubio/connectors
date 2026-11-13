package io.casehub.connectors.email.microsoft;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MailMapperTest {

    @Test
    void toMailboxMapsFields() {
        var json = Json.createObjectBuilder()
                .add("id", "inbox-id")
                .add("displayName", "Inbox")
                .add("unreadItemCount", 5)
                .build();
        var mailbox = MailMapper.toMailbox(json);
        assertThat(mailbox.id()).isEqualTo("inbox-id");
        assertThat(mailbox.name()).isEqualTo("Inbox");
        assertThat(mailbox.unreadCount()).isEqualTo(5);
    }

    @Test
    void toEmailSummaryMapsFields() {
        var json = messageJson("msg-1", "alice@example.com", "Hello",
                "2026-11-13T10:00:00Z", true);
        var summary = MailMapper.toEmailSummary(json, "inbox-id");
        assertThat(summary.id()).isEqualTo("msg-1");
        assertThat(summary.mailboxId()).isEqualTo("inbox-id");
        assertThat(summary.from()).isEqualTo("alice@example.com");
        assertThat(summary.subject()).isEqualTo("Hello");
        assertThat(summary.read()).isTrue();
        assertThat(summary.receivedAt()).isNotNull();
    }

    @Test
    void toEmailMessageMapsBodyAndRecipients() {
        var json = Json.createObjectBuilder()
                .add("id", "msg-2")
                .add("subject", "Test")
                .add("from", emailAddress("sender@example.com"))
                .add("toRecipients", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("emailAddress", Json.createObjectBuilder()
                                        .add("address", "to@example.com"))))
                .add("ccRecipients", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("emailAddress", Json.createObjectBuilder()
                                        .add("address", "cc@example.com"))))
                .add("body", Json.createObjectBuilder()
                        .add("contentType", "html")
                        .add("content", "<b>Hello</b>"))
                .add("receivedDateTime", "2026-11-13T10:00:00Z")
                .add("isRead", false)
                .add("attachments", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("id", "att-1")
                                .add("name", "file.pdf")
                                .add("contentType", "application/pdf")
                                .add("size", 1024)))
                .build();
        var msg = MailMapper.toEmailMessage(json, "inbox-id");
        assertThat(msg.id()).isEqualTo("msg-2");
        assertThat(msg.from()).isEqualTo("sender@example.com");
        assertThat(msg.to()).containsExactly("to@example.com");
        assertThat(msg.cc()).containsExactly("cc@example.com");
        assertThat(msg.bodyHtml()).isEqualTo("<b>Hello</b>");
        assertThat(msg.bodyText()).isEmpty();
        assertThat(msg.attachments()).hasSize(1);
        assertThat(msg.attachments().get(0).filename()).isEqualTo("file.pdf");
        assertThat(msg.attachments().get(0).size()).isEqualTo(1024);
    }

    @Test
    void toEmailMessageHandlesTextBody() {
        var json = Json.createObjectBuilder()
                .add("id", "msg-3")
                .add("subject", "Plain")
                .add("body", Json.createObjectBuilder()
                        .add("contentType", "text")
                        .add("content", "Plain text"))
                .add("receivedDateTime", "2026-11-13T10:00:00Z")
                .add("isRead", true)
                .build();
        var msg = MailMapper.toEmailMessage(json, "inbox-id");
        assertThat(msg.bodyText()).isEqualTo("Plain text");
        assertThat(msg.bodyHtml()).isEmpty();
    }

    @Test
    void toEmailMessageHandlesMissingFields() {
        var json = Json.createObjectBuilder()
                .add("id", "msg-4")
                .build();
        var msg = MailMapper.toEmailMessage(json, "inbox-id");
        assertThat(msg.id()).isEqualTo("msg-4");
        assertThat(msg.from()).isEmpty();
        assertThat(msg.to()).isEmpty();
        assertThat(msg.cc()).isEmpty();
        assertThat(msg.attachments()).isEmpty();
    }

    private static JsonObject messageJson(String id, String from, String subject,
            String receivedDateTime, boolean isRead) {
        var builder = Json.createObjectBuilder()
                .add("id", id)
                .add("subject", subject)
                .add("receivedDateTime", receivedDateTime)
                .add("isRead", isRead);
        if (from != null) {
            builder.add("from", emailAddress(from));
        }
        return builder.build();
    }

    private static JsonObject emailAddress(String address) {
        return Json.createObjectBuilder()
                .add("emailAddress", Json.createObjectBuilder()
                        .add("address", address))
                .build();
    }
}
