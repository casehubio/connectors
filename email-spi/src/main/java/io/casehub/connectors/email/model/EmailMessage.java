package io.casehub.connectors.email.model;

import java.time.Instant;
import java.util.List;

public record EmailMessage(String id, String mailboxId,
                           String messageId,
                           String from, List<String> to, List<String> cc,
                           String subject, String bodyText, String bodyHtml,
                           Instant receivedAt, boolean read,
                           List<EmailAttachment> attachments) {}
