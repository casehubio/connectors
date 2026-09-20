package io.casehub.connectors.email.model;

import java.time.Instant;

public record EmailSummary(String id, String mailboxId,
                           String messageId,
                           String from, String subject,
                           Instant receivedAt, boolean read) {}
