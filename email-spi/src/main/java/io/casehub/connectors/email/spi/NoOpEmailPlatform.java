package io.casehub.connectors.email.spi;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.email.model.EmailMessage;
import io.casehub.connectors.email.model.EmailSummary;
import io.casehub.connectors.email.model.Mailbox;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;

@DefaultBean
@ApplicationScoped
public class NoOpEmailPlatform implements EmailPlatform {

    private static final EmailOperations NOOP = new EmailOperations() {
        @Override
        public List<Mailbox> listMailboxes() {return List.of();}

        @Override
        public Page<EmailSummary> listMessages(String m, Instant f, Instant t, PageRequest p) {
            return Page.of(List.of());
        }

        @Override
        public EmailMessage getMessage(String m, String id) {
            throw new UnsupportedOperationException("No email provider configured");
        }

        @Override
        public byte[] getAttachmentContent(String m, String id, String a) {
            throw new UnsupportedOperationException("No email provider configured");
        }

        @Override
        public Page<EmailSummary> search(String q, PageRequest p) {
            return Page.of(List.of());
        }
    };

    @Override
    public String id() {
        return "none";
    }

    @Override
    public EmailOperations emailOps(String userId) {
        return NOOP;
    }
}
