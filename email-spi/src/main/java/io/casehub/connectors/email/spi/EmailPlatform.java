package io.casehub.connectors.email.spi;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.PlatformExtensible;
import io.casehub.connectors.email.model.EmailMessage;
import io.casehub.connectors.email.model.EmailSummary;
import io.casehub.connectors.email.model.Mailbox;
import io.casehub.platform.simulation.SimulationEligible;

import java.time.Instant;
import java.util.List;

@SimulationEligible(name = "email-platform")
public interface EmailPlatform extends PlatformExtensible {

    String id();

    EmailOperations emailOps(String userId);

    interface EmailOperations {
        List<Mailbox> listMailboxes();

        Page<EmailSummary> listMessages(String mailboxId, Instant from, Instant to, PageRequest pagination);

        EmailMessage getMessage(String mailboxId, String messageId);

        byte[] getAttachmentContent(String mailboxId, String messageId, String attachmentId);

        Page<EmailSummary> search(String query, PageRequest pagination);
    }
}
