package io.casehub.connectors.email.spi;

import java.time.Instant;
import java.util.List;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.email.model.EmailMessage;
import io.casehub.connectors.email.model.EmailSummary;
import io.casehub.connectors.email.model.Mailbox;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpEmailPlatform implements EmailPlatform {

    @Override
    public String id() {
        return "none";
    }

    @Override
    public List<Mailbox> listMailboxes() {
        return List.of();
    }

    @Override
    public Page<EmailSummary> listMessages(final String mailboxId,
            final Instant from, final Instant to, final PageRequest pagination) {
        return Page.of(List.of());
    }

    @Override
    public EmailMessage getMessage(final String mailboxId,
            final String messageId) {
        throw new UnsupportedOperationException("No email provider configured");
    }

    @Override
    public byte[] getAttachmentContent(final String mailboxId,
            final String messageId, final String attachmentId) {
        throw new UnsupportedOperationException("No email provider configured");
    }
}
