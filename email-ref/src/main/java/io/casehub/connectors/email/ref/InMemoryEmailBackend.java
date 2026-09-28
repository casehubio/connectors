package io.casehub.connectors.email.ref;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.email.model.EmailAttachment;
import io.casehub.connectors.email.model.EmailMessage;
import io.casehub.connectors.email.model.EmailSummary;
import io.casehub.connectors.email.model.Mailbox;

@ApplicationScoped
public class InMemoryEmailBackend implements EmailBackend {

    private final List<Mailbox> mailboxes;
    private final Map<String, List<EmailMessage>> messages;
    private final Map<String, byte[]> attachmentContent;

    public InMemoryEmailBackend() {
        this.messages = new LinkedHashMap<>();
        this.attachmentContent = new LinkedHashMap<>();
        this.mailboxes = new ArrayList<>();
        loadData();
    }

    private void loadData() {
        var inbox = new ArrayList<EmailMessage>();
        var sent = new ArrayList<EmailMessage>();
        var archive = new ArrayList<EmailMessage>();

        inbox.add(new EmailMessage("msg-001", "inbox", "<msg-001@mail.example.com>",
                "alice@example.com", List.of("user@example.com"), List.of(),
                "Q3 Planning Session",
                "Hi, let's schedule the Q3 planning session for next week.", null,
                Instant.parse("2026-09-15T09:30:00Z"), true, List.of()));

        inbox.add(new EmailMessage("msg-002", "inbox", "<msg-002@mail.example.com>",
                "bob@example.com", List.of("user@example.com"), List.of("carol@example.com"),
                "Invoice #4821 attached",
                "Please find the invoice attached.",
                "<html><body><p>Please find the invoice attached.</p></body></html>",
                Instant.parse("2026-09-16T14:15:00Z"), true,
                List.of(new EmailAttachment("att-001", "invoice-4821.pdf", "application/pdf", 24576))));
        attachmentContent.put("inbox/msg-002/att-001", new byte[24576]);

        inbox.add(new EmailMessage("msg-003", "inbox", "<msg-003@mail.example.com>",
                "noreply@github.com", List.of("user@example.com"), List.of(),
                "[connectors] PR #112 merged",
                "Your pull request #112 has been merged into main.",
                "<html><body><p>Your pull request #112 has been merged into main.</p></body></html>",
                Instant.parse("2026-09-17T08:00:00Z"), false, List.of()));

        inbox.add(new EmailMessage("msg-004", "inbox", "<msg-004@mail.example.com>",
                "carol@example.com", List.of("user@example.com", "dave@example.com"), List.of(),
                "Architecture review notes",
                "Attached are the notes from today's architecture review.", null,
                Instant.parse("2026-09-18T16:45:00Z"), false,
                List.of(new EmailAttachment("att-002", "arch-review-notes.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 18432))));
        attachmentContent.put("inbox/msg-004/att-002", new byte[18432]);

        inbox.add(new EmailMessage("msg-005", "inbox", "<msg-005@mail.example.com>",
                "dave@example.com", List.of("user@example.com"), List.of(),
                "Re: Architecture review notes",
                "Thanks for sharing. I have a few comments inline.", null,
                Instant.parse("2026-09-19T10:20:00Z"), false, List.of()));

        inbox.add(new EmailMessage("msg-006", "inbox", null,
                "newsletter@techweekly.com", List.of("user@example.com"), List.of(),
                "This week in tech — September 2026",
                "Your weekly tech roundup is here.",
                "<html><body><h1>Tech Weekly</h1><p>Your weekly tech roundup.</p></body></html>",
                Instant.parse("2026-09-20T06:00:00Z"), true, List.of()));

        sent.add(new EmailMessage("msg-101", "sent", "<msg-101@mail.example.com>",
                "user@example.com", List.of("alice@example.com"), List.of(),
                "Re: Q3 Planning Session",
                "Thursday 2pm works for me.", null,
                Instant.parse("2026-09-15T10:05:00Z"), true, List.of()));

        sent.add(new EmailMessage("msg-102", "sent", "<msg-102@mail.example.com>",
                "user@example.com", List.of("bob@example.com"), List.of("carol@example.com"),
                "Re: Invoice #4821 attached",
                "Received, I'll process it today.", null,
                Instant.parse("2026-09-16T15:30:00Z"), true, List.of()));

        archive.add(new EmailMessage("msg-201", "archive", "<msg-201@mail.example.com>",
                "hr@example.com", List.of("user@example.com"), List.of(),
                "Holiday policy update",
                "Please review the updated holiday policy.",
                "<html><body><p>Please review the updated holiday policy.</p></body></html>",
                Instant.parse("2026-08-01T09:00:00Z"), true, List.of()));

        messages.put("inbox", inbox);
        messages.put("sent", sent);
        messages.put("archive", archive);

        int inboxUnread = (int) inbox.stream().filter(m -> !m.read()).count();
        mailboxes.add(new Mailbox("inbox", "Inbox", inboxUnread));
        mailboxes.add(new Mailbox("sent", "Sent", 0));
        mailboxes.add(new Mailbox("archive", "Archive", 0));
    }

    @Override
    public List<Mailbox> listMailboxes() {
        return List.copyOf(mailboxes);
    }

    @Override
    public Page<EmailSummary> listMessages(String mailboxId, Instant from,
                                           Instant to, PageRequest pagination) {
        var mailboxMessages = messages.getOrDefault(mailboxId, List.of());
        var filtered = mailboxMessages.stream()
                .filter(m -> !m.receivedAt().isBefore(from) && m.receivedAt().isBefore(to))
                .toList();

        int startIndex = 0;
        if (pagination.cursor() != null) {
            try {
                startIndex = Integer.parseInt(pagination.cursor());
            } catch (NumberFormatException e) {
                startIndex = 0;
            }
        }

        int endIndex = Math.min(startIndex + pagination.pageSize(), filtered.size());
        var pageItems = filtered.subList(startIndex, endIndex).stream()
                .map(m -> new EmailSummary(m.id(), m.mailboxId(), m.messageId(),
                        m.from(), m.subject(), m.receivedAt(), m.read()))
                .toList();

        boolean hasMore = endIndex < filtered.size();
        String nextCursor = hasMore ? String.valueOf(endIndex) : null;
        return new Page<>(pageItems, nextCursor, hasMore);
    }

    @Override
    public EmailMessage getMessage(String mailboxId, String messageId) {
        var mailboxMessages = messages.get(mailboxId);
        if (mailboxMessages == null) {
            throw new NoSuchElementException(
                    "Mailbox '" + mailboxId + "' not found");
        }
        return mailboxMessages.stream()
                .filter(m -> m.id().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "Message '" + messageId + "' not found in mailbox '" + mailboxId + "'"));
    }

    @Override
    public byte[] getAttachmentContent(String mailboxId, String messageId,
                                       String attachmentId) {
        var message = getMessage(mailboxId, messageId);
        var attachment = message.attachments().stream()
                .filter(a -> a.id().equals(attachmentId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "Attachment '" + attachmentId + "' not found on message '"
                        + messageId + "' in mailbox '" + mailboxId + "'"));
        String key = mailboxId + "/" + messageId + "/" + attachmentId;
        byte[] content = attachmentContent.get(key);
        if (content == null) {
            throw new NoSuchElementException(
                    "Content not found for attachment '" + attachmentId + "'");
        }
        return content;
    }
}
