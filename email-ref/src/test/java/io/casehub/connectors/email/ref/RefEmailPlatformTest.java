package io.casehub.connectors.email.ref;

import io.casehub.connectors.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefEmailPlatformTest {

    private RefEmailPlatform platform;
    private InMemoryEmailBackend backend;

    @BeforeEach
    void setUp() {
        backend = new InMemoryEmailBackend();
        platform = new RefEmailPlatform(backend);
    }

    @Test
    void id_isRef() {
        assertThat(platform.id()).isEqualTo("ref");
    }

    @Test
    void listMailboxes_returnsPreloadedMailboxes() {
        var mailboxes = platform.listMailboxes();
        assertThat(mailboxes).isNotEmpty();
        assertThat(mailboxes).extracting("name")
                .contains("Inbox", "Sent", "Archive");
    }

    @Test
    void listMailboxes_inboxHasUnreadMessages() {
        var inbox = platform.listMailboxes().stream()
                .filter(m -> m.name().equals("Inbox"))
                .findFirst().orElseThrow();
        assertThat(inbox.unreadCount()).isGreaterThan(0);
    }

    @Test
    void listMessages_returnsMessagesInDateRange() {
        var inbox = platform.listMailboxes().stream()
                .filter(m -> m.name().equals("Inbox"))
                .findFirst().orElseThrow();
        var page = platform.listMessages(inbox.id(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(50));
        assertThat(page.items()).isNotEmpty();
        assertThat(page.items()).allSatisfy(s -> {
            assertThat(s.mailboxId()).isEqualTo(inbox.id());
            assertThat(s.receivedAt()).isAfterOrEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
            assertThat(s.receivedAt()).isBefore(Instant.parse("2026-12-31T00:00:00Z"));
        });
    }

    @Test
    void listMessages_filtersOutsideDateRange() {
        var inbox = platform.listMailboxes().stream()
                .filter(m -> m.name().equals("Inbox"))
                .findFirst().orElseThrow();
        var page = platform.listMessages(inbox.id(),
                Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2020-01-02T00:00:00Z"),
                PageRequest.first(50));
        assertThat(page.items()).isEmpty();
    }

    @Test
    void listMessages_unknownMailbox_returnsEmpty() {
        var page = platform.listMessages("nonexistent",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(50));
        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void listMessages_pagination_respectsPageSize() {
        var inbox = platform.listMailboxes().stream()
                .filter(m -> m.name().equals("Inbox"))
                .findFirst().orElseThrow();
        var firstPage = platform.listMessages(inbox.id(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(2));
        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isNotNull();
    }

    @Test
    void listMessages_pagination_cursorFetchesNextPage() {
        var inbox = platform.listMailboxes().stream()
                .filter(m -> m.name().equals("Inbox"))
                .findFirst().orElseThrow();
        var firstPage = platform.listMessages(inbox.id(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(2));
        var secondPage = platform.listMessages(inbox.id(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                new PageRequest(firstPage.nextCursor(), 2));
        assertThat(secondPage.items()).isNotEmpty();
        assertThat(secondPage.items())
                .extracting("id")
                .doesNotContainAnyElementsOf(
                        firstPage.items().stream().map(s -> s.id()).toList());
    }

    @Test
    void listMessages_mixOfReadAndUnread() {
        var inbox = platform.listMailboxes().stream()
                .filter(m -> m.name().equals("Inbox"))
                .findFirst().orElseThrow();
        var page = platform.listMessages(inbox.id(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(50));
        var readCount = page.items().stream().filter(s -> s.read()).count();
        var unreadCount = page.items().stream().filter(s -> !s.read()).count();
        assertThat(readCount).isGreaterThan(0);
        assertThat(unreadCount).isGreaterThan(0);
    }

    @Test
    void getMessage_returnsFullMessage() {
        var inbox = platform.listMailboxes().stream()
                .filter(m -> m.name().equals("Inbox"))
                .findFirst().orElseThrow();
        var summaries = platform.listMessages(inbox.id(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(50));
        var firstId = summaries.items().getFirst().id();

        var message = platform.getMessage(inbox.id(), firstId);

        assertThat(message.id()).isEqualTo(firstId);
        assertThat(message.mailboxId()).isEqualTo(inbox.id());
        assertThat(message.from()).isNotNull();
        assertThat(message.to()).isNotEmpty();
        assertThat(message.subject()).isNotNull();
        assertThat(message.bodyText()).isNotNull();
    }

    @Test
    void getMessage_unknownMessage_throws() {
        var inbox = platform.listMailboxes().stream()
                .filter(m -> m.name().equals("Inbox"))
                .findFirst().orElseThrow();
        assertThatThrownBy(() -> platform.getMessage(inbox.id(), "nonexistent"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getMessage_unknownMailbox_throws() {
        assertThatThrownBy(() -> platform.getMessage("nonexistent", "msg-1"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getMessage_someMessagesHaveAttachments() {
        var inbox = platform.listMailboxes().stream()
                .filter(m -> m.name().equals("Inbox"))
                .findFirst().orElseThrow();
        var summaries = platform.listMessages(inbox.id(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(50));
        var hasAttachment = summaries.items().stream()
                .map(s -> platform.getMessage(inbox.id(), s.id()))
                .anyMatch(m -> !m.attachments().isEmpty());
        assertThat(hasAttachment).isTrue();
    }

    @Test
    void getAttachmentContent_returnsNonEmptyBytes() {
        var inbox = platform.listMailboxes().stream()
                .filter(m -> m.name().equals("Inbox"))
                .findFirst().orElseThrow();
        var summaries = platform.listMessages(inbox.id(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(50));
        var messageWithAttachment = summaries.items().stream()
                .map(s -> platform.getMessage(inbox.id(), s.id()))
                .filter(m -> !m.attachments().isEmpty())
                .findFirst().orElseThrow();
        var attachment = messageWithAttachment.attachments().getFirst();

        var content = platform.getAttachmentContent(
                inbox.id(), messageWithAttachment.id(), attachment.id());

        assertThat(content).isNotEmpty();
        assertThat(content).hasSize((int) attachment.size());
    }

    @Test
    void getAttachmentContent_unknownAttachment_throws() {
        var inbox = platform.listMailboxes().stream()
                .filter(m -> m.name().equals("Inbox"))
                .findFirst().orElseThrow();
        var summaries = platform.listMessages(inbox.id(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(50));
        var firstId = summaries.items().getFirst().id();
        assertThatThrownBy(() ->
                platform.getAttachmentContent(inbox.id(), firstId, "nonexistent"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getAttachmentContent_unknownMessage_throws() {
        var inbox = platform.listMailboxes().stream()
                .filter(m -> m.name().equals("Inbox"))
                .findFirst().orElseThrow();
        assertThatThrownBy(() ->
                platform.getAttachmentContent(inbox.id(), "nonexistent", "att-1"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void mailboxUnreadCount_matchesActualUnreadMessages() {
        var inbox = platform.listMailboxes().stream()
                .filter(m -> m.name().equals("Inbox"))
                .findFirst().orElseThrow();
        var page = platform.listMessages(inbox.id(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(50));
        var actualUnread = page.items().stream()
                .filter(s -> !s.read()).count();
        assertThat(inbox.unreadCount()).isEqualTo((int) actualUnread);
    }

    @Test
    void sentMailbox_hasMessages() {
        var sent = platform.listMailboxes().stream()
                .filter(m -> m.name().equals("Sent"))
                .findFirst().orElseThrow();
        var page = platform.listMessages(sent.id(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(50));
        assertThat(page.items()).isNotEmpty();
    }

    @Test
    void differentMailboxes_haveIsolatedMessages() {
        var mailboxes = platform.listMailboxes();
        assertThat(mailboxes.size()).isGreaterThanOrEqualTo(2);
        var first = mailboxes.getFirst();
        var second = mailboxes.get(1);

        var firstMessages = platform.listMessages(first.id(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(50));
        var secondMessages = platform.listMessages(second.id(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(50));

        var firstIds = firstMessages.items().stream().map(s -> s.id()).toList();
        var secondIds = secondMessages.items().stream().map(s -> s.id()).toList();
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
    }

    @Test
    void search_matchesSubject() {
        var page = platform.search("invoice", PageRequest.first(50));
        assertThat(page.items()).isNotEmpty();
        assertThat(page.items()).allSatisfy(s ->
                                                    assertThat(s.subject().toLowerCase()).contains("invoice"));
    }

    @Test
    void search_matchesFrom() {
        var allMessages = platform.listMessages("inbox",
                                                Instant.parse("2026-01-01T00:00:00Z"),
                                                Instant.parse("2026-12-31T00:00:00Z"),
                                                PageRequest.first(50));
        var knownSender = allMessages.items().getFirst().from();
        var senderPart  = knownSender.split("@")[0];

        var page = platform.search(senderPart, PageRequest.first(50));
        assertThat(page.items()).isNotEmpty();
    }

    @Test
    void search_noMatch_returnsEmpty() {
        var page = platform.search("xyznonexistentxyz", PageRequest.first(50));
        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void search_caseInsensitive() {
        var lowerPage = platform.search("invoice", PageRequest.first(50));
        var upperPage = platform.search("INVOICE", PageRequest.first(50));
        assertThat(lowerPage.items()).hasSameSizeAs(upperPage.items());
    }

    @Test
    void search_pagination_respectsPageSize() {
        var allResults = platform.search("", PageRequest.first(50));
        if (allResults.items().size() <= 2) {return;}

        var firstPage = platform.search("", PageRequest.first(2));
        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isNotNull();

        var secondPage = platform.search("", new PageRequest(firstPage.nextCursor(), 2));
        assertThat(secondPage.items()).isNotEmpty();
        assertThat(secondPage.items())
                .extracting("id")
                .doesNotContainAnyElementsOf(
                        firstPage.items().stream().map(s -> s.id()).toList());
    }

    @Test
    void search_spansAllMailboxes() {
        var results = platform.search("", PageRequest.first(50));
        var mailboxIds = results.items().stream()
                                .map(s -> s.mailboxId())
                                .distinct()
                                .toList();
        assertThat(mailboxIds).hasSizeGreaterThan(1);
    }


}
