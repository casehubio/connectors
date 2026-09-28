package io.casehub.connectors.email.ref;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.email.model.EmailMessage;
import io.casehub.connectors.email.model.EmailSummary;
import io.casehub.connectors.email.model.Mailbox;
import io.casehub.connectors.email.spi.EmailPlatform;

import java.time.Instant;
import java.util.List;

public class RefEmailPlatform implements EmailPlatform {

    private final EmailBackend backend;

    public RefEmailPlatform(EmailBackend backend) {
        this.backend = backend;
    }

    @Override
    public String id() {return "ref";}

    @Override
    public List<Mailbox> listMailboxes() {return backend.listMailboxes();}

    @Override
    public Page<EmailSummary> listMessages(String mailboxId, Instant from,
                                           Instant to, PageRequest pagination) {return backend.listMessages(mailboxId, from, to, pagination);}

    @Override
    public EmailMessage getMessage(String mailboxId, String messageId) {return backend.getMessage(mailboxId, messageId);}

    @Override
    public byte[] getAttachmentContent(String mailboxId, String messageId,
                                       String attachmentId) {return backend.getAttachmentContent(mailboxId, messageId, attachmentId);}
}
