package io.casehub.connectors.email.ref;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedLoaderTest {

    @Test
    void loadsLifeDomainMessages() {
        var messages = SeedLoader.loadMessages();
        assertThat(messages).hasSize(12);
        assertThat(messages).extracting("from")
                            .anyMatch(f -> ((String) f).contains("admiral"));
    }

    @Test
    void inboxHasNineMessages() {
        var inbox = SeedLoader.loadMessages().stream()
                              .filter(m -> "inbox".equals(m.mailboxId()))
                              .toList();
        assertThat(inbox).hasSize(9);
    }

    @Test
    void messageWithAttachment() {
        var msg = SeedLoader.loadMessages().stream()
                            .filter(m -> "msg-002".equals(m.id()))
                            .findFirst().orElseThrow();
        assertThat(msg.attachments()).hasSize(1);
        assertThat(msg.attachments().getFirst().filename()).isEqualTo("admiral-renewal-2026.pdf");
        assertThat(msg.attachments().getFirst().size()).isEqualTo(24576);
    }

    @Test
    void unreadMessages() {
        var unread = SeedLoader.loadMessages().stream()
                               .filter(m -> !m.read())
                               .toList();
        assertThat(unread).hasSizeGreaterThanOrEqualTo(3);
    }
}
