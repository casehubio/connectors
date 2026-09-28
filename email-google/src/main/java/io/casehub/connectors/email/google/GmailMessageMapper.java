package io.casehub.connectors.email.google;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;

import io.casehub.connectors.email.model.EmailAttachment;
import io.casehub.connectors.email.model.EmailMessage;
import io.casehub.connectors.email.model.EmailSummary;

final class GmailMessageMapper {

    private GmailMessageMapper() {}

    static EmailSummary toEmailSummary(Message message, String mailboxId) {
        String from = getHeader(message, "From");
        String subject = getHeader(message, "Subject");
        String messageId = getHeader(message, "Message-ID");
        Instant receivedAt = Instant.ofEpochMilli(message.getInternalDate());
        boolean read = message.getLabelIds() == null
                || !message.getLabelIds().contains("UNREAD");

        return new EmailSummary(message.getId(), mailboxId, messageId,
                from, subject, receivedAt, read);
    }

    static EmailMessage toEmailMessage(Message message, String mailboxId) {
        String from = getHeader(message, "From");
        String subject = getHeader(message, "Subject");
        String messageId = getHeader(message, "Message-ID");
        Instant receivedAt = Instant.ofEpochMilli(message.getInternalDate());
        boolean read = message.getLabelIds() == null
                || !message.getLabelIds().contains("UNREAD");

        List<String> to = parseAddressList(getHeader(message, "To"));
        List<String> cc = parseAddressList(getHeader(message, "Cc"));

        String bodyText = null;
        String bodyHtml = null;
        List<EmailAttachment> attachments = new ArrayList<>();

        MessagePart payload = message.getPayload();
        if (payload != null) {
            if (payload.getParts() != null) {
                for (MessagePart part : payload.getParts()) {
                    if ("text/plain".equals(part.getMimeType()) && bodyText == null) {
                        bodyText = decodeBody(part);
                    } else if ("text/html".equals(part.getMimeType()) && bodyHtml == null) {
                        bodyHtml = decodeBody(part);
                    } else if (part.getBody() != null && part.getBody().getAttachmentId() != null) {
                        attachments.add(new EmailAttachment(
                                part.getBody().getAttachmentId(),
                                part.getFilename(),
                                part.getMimeType(),
                                part.getBody().getSize()));
                    }
                }
            } else if (payload.getBody() != null && payload.getBody().getData() != null) {
                if ("text/plain".equals(payload.getMimeType())) {
                    bodyText = decodeBody(payload);
                } else if ("text/html".equals(payload.getMimeType())) {
                    bodyHtml = decodeBody(payload);
                }
            }
        }

        return new EmailMessage(message.getId(), mailboxId, messageId,
                from, to, cc, subject, bodyText, bodyHtml,
                receivedAt, read, attachments);
    }

    private static String getHeader(Message message, String name) {
        if (message.getPayload() == null || message.getPayload().getHeaders() == null) {
            return null;
        }
        return message.getPayload().getHeaders().stream()
                .filter(h -> name.equalsIgnoreCase(h.getName()))
                .map(h -> h.getValue())
                .findFirst()
                .orElse(null);
    }

    private static List<String> parseAddressList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("\\s*,\\s*"));
    }

    private static String decodeBody(MessagePart part) {
        if (part.getBody() == null || part.getBody().getData() == null) {
            return null;
        }
        return new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
    }
}
