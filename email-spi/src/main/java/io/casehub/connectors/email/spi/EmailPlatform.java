package io.casehub.connectors.email.spi;

import java.time.Instant;
import java.util.List;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.email.model.EmailMessage;
import io.casehub.connectors.email.model.EmailSummary;
import io.casehub.connectors.email.model.Mailbox;
import io.casehub.platform.simulation.SimulationEligible;

@SimulationEligible(name = "email-platform")
public interface EmailPlatform {

    String id();

    List<Mailbox> listMailboxes();

    Page<EmailSummary> listMessages(String mailboxId,
                                    Instant from, Instant to,
                                    PageRequest pagination);

    EmailMessage getMessage(String mailboxId, String messageId);

    byte[] getAttachmentContent(String mailboxId, String messageId,
                                String attachmentId);
}
