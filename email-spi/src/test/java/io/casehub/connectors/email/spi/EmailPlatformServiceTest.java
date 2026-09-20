package io.casehub.connectors.email.spi;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.email.model.EmailMessage;
import io.casehub.connectors.email.model.EmailSummary;
import io.casehub.connectors.email.model.Mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailPlatformServiceTest {

    static class StubPlatform implements EmailPlatform {
        private final String platformId;
        StubPlatform(String id) { this.platformId = id; }
        @Override public String id() { return platformId; }
        @Override public List<Mailbox> listMailboxes() { return List.of(); }
        @Override public Page<EmailSummary> listMessages(String m, Instant f,
                Instant t, PageRequest p) { return Page.of(List.of()); }
        @Override public EmailMessage getMessage(String m, String id) { return null; }
        @Override public byte[] getAttachmentContent(String m, String mid,
                String aid) { return new byte[0]; }
    }

    @Test
    void platform_knownId_returnsPlatform() {
        var service = new EmailPlatformService(List.of(new StubPlatform("imap")));
        assertThat(service.platform("imap").id()).isEqualTo("imap");
    }

    @Test
    void platform_unknownId_throws() {
        var service = new EmailPlatformService(List.of(new StubPlatform("imap")));
        assertThatThrownBy(() -> service.platform("graph"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("graph")
                .hasMessageContaining("imap");
    }

    @Test
    void supports_knownId_returnsTrue() {
        var service = new EmailPlatformService(List.of(new StubPlatform("imap")));
        assertThat(service.supports("imap")).isTrue();
    }

    @Test
    void supports_unknownId_returnsFalse() {
        var service = new EmailPlatformService(List.of(new StubPlatform("imap")));
        assertThat(service.supports("graph")).isFalse();
    }

    @Test
    void ids_returnsAllRegistered() {
        var service = new EmailPlatformService(
                List.of(new StubPlatform("imap"), new StubPlatform("graph")));
        assertThat(service.ids()).containsExactlyInAnyOrder("imap", "graph");
    }

    @Test
    void duplicateId_throwsAtConstruction() {
        assertThatThrownBy(() -> new EmailPlatformService(
                List.of(new StubPlatform("imap"), new StubPlatform("imap"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("imap");
    }
}
