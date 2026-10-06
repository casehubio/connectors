package io.casehub.connectors.email.ref;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.email.model.EmailMessage;
import io.casehub.connectors.email.model.EmailSummary;
import io.casehub.connectors.email.model.Mailbox;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

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
        var allMessages = SeedLoader.loadMessages();
        var grouped     = new LinkedHashMap<String, List<EmailMessage>>();
        allMessages.forEach(m ->
                                    grouped.computeIfAbsent(m.mailboxId(), k -> new ArrayList<>()).add(m));
        messages.putAll(grouped);

        allMessages.stream()
                   .flatMap(m -> m.attachments().stream()
                                  .map(a -> Map.entry(m.mailboxId() + "/" + m.id() + "/" + a.id(), new byte[(int) a.size()])))
                   .forEach(e -> attachmentContent.put(e.getKey(), e.getValue()));

        grouped.forEach((mailboxId, msgs) -> {
            int    unread      = (int) msgs.stream().filter(m -> !m.read()).count();
            String displayName = mailboxId.substring(0, 1).toUpperCase() + mailboxId.substring(1);
            mailboxes.add(new Mailbox(mailboxId, displayName, unread));
        });
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

    @Override
    public Page<EmailSummary> search(String query, PageRequest pagination) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        var matched = messages.values().stream()
                              .flatMap(Collection::stream)
                              .filter(m -> matches(m, lowerQuery))
                              .map(m -> new EmailSummary(m.id(), m.mailboxId(), m.messageId(),
                                                         m.from(), m.subject(), m.receivedAt(), m.read()))
                              .toList();

        int startIndex = 0;
        if (pagination.cursor() != null) {
            try {
                startIndex = Integer.parseInt(pagination.cursor());
            } catch (NumberFormatException e) {
                startIndex = 0;
            }
        }

        int     endIndex   = Math.min(startIndex + pagination.pageSize(), matched.size());
        var     pageItems  = matched.subList(startIndex, endIndex);
        boolean hasMore    = endIndex < matched.size();
        String  nextCursor = hasMore ? String.valueOf(endIndex) : null;
        return new Page<>(pageItems, nextCursor, hasMore);
    }

    private static boolean matches(EmailMessage m, String lowerQuery) {
        return containsIgnoreCase(m.subject(), lowerQuery)
               || containsIgnoreCase(m.from(), lowerQuery)
               || containsIgnoreCase(m.bodyText(), lowerQuery);
    }

    private static boolean containsIgnoreCase(String text, String lowerQuery) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowerQuery);
    }


}
