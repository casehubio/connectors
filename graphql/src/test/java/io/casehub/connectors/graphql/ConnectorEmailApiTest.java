package io.casehub.connectors.graphql;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.email.model.EmailAttachment;
import io.casehub.connectors.email.model.EmailMessage;
import io.casehub.connectors.email.model.EmailSummary;
import io.casehub.connectors.email.model.Mailbox;
import io.casehub.connectors.email.spi.EmailPlatform;
import io.casehub.connectors.email.spi.EmailPlatformService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorEmailApiTest {

    private final ConnectorEmailApi api;

    ConnectorEmailApiTest() {
        var service = new EmailPlatformService(List.of(new StubEmailPlatform()));
        api = new ConnectorEmailApi();
        api.emailService = service;
    }

    @Test
    void listMailboxes_returnsMailboxes() {
        var mailboxes = api.listMailboxes("stub-email");
        assertThat(mailboxes).hasSize(2);
        assertThat(mailboxes).extracting("id")
                .containsExactly("inbox", "sent");
    }

    @Test
    void listMessages_returnsPage() {
        var page = api.listMessages("stub-email", "inbox",
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z"),
                null, 50);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().subject()).isEqualTo("Test subject");
    }

    @Test
    void getMessage_returnsFull() {
        var message = api.getMessage("stub-email", "inbox", "msg-001");
        assertThat(message).isNotNull();
        assertThat(message.id()).isEqualTo("msg-001");
        assertThat(message.subject()).isEqualTo("Test subject");
        assertThat(message.bodyText()).isEqualTo("Test body");
    }

    @Test
    void getAttachmentContent_returnsBase64() {
        var content = api.getAttachmentContent("stub-email", "inbox",
                "msg-001", "att-001");
        assertThat(content).isNotNull();
        assertThat(content).isNotEmpty();
    }

    @Test
    void unknownPlatform_throwsIllegalArgument() {
        assertThatThrownBy(() -> api.listMailboxes("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static class StubEmailPlatform implements EmailPlatform {
        private static final Instant MSG_TIME = Instant.parse("2026-10-15T10:00:00Z");
        private static final EmailMessage TEST_MSG = new EmailMessage(
                "msg-001", "inbox", "<msg-001@test.com>",
                "sender@test.com", List.of("recipient@test.com"), List.of(),
                "Test subject", "Test body", null,
                MSG_TIME, true,
                List.of(new EmailAttachment("att-001", "test.pdf", "application/pdf", 1024)));

        @Override public String id() { return "stub-email"; }

        @Override
        public List<Mailbox> listMailboxes() {
            return List.of(
                    new Mailbox("inbox", "Inbox", 1),
                    new Mailbox("sent", "Sent", 0));
        }

        @Override
        public Page<EmailSummary> listMessages(String mailboxId, Instant from,
                                               Instant to, PageRequest pagination) {
            if (!"inbox".equals(mailboxId)) return new Page<>(List.of(), null, false);
            var summary = new EmailSummary("msg-001", "inbox", "<msg-001@test.com>",
                    "sender@test.com", "Test subject", MSG_TIME, true);
            return new Page<>(List.of(summary), null, false);
        }

        @Override
        public EmailMessage getMessage(String mailboxId, String messageId) {
            return TEST_MSG;
        }

        @Override
        public byte[] getAttachmentContent(String mailboxId, String messageId,
                                           String attachmentId) {
            return "test-content".getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Page<EmailSummary> search(String query, PageRequest pagination) {
            return new Page<>(List.of(), null, false);
        }
    }
}
