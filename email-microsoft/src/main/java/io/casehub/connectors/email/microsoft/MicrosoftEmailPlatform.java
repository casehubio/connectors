package io.casehub.connectors.email.microsoft;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.email.model.EmailMessage;
import io.casehub.connectors.email.model.EmailSummary;
import io.casehub.connectors.email.model.Mailbox;
import io.casehub.connectors.email.spi.EmailPlatform;
import io.casehub.connectors.microsoft.TokenRetry;
import io.casehub.platform.api.authn.ServiceConnectionProvider;

import java.time.Instant;
import java.util.List;

public class MicrosoftEmailPlatform implements EmailPlatform {

    private final MicrosoftMailClient mailClient;
    private final ServiceConnectionProvider connectionProvider;

    public MicrosoftEmailPlatform(MicrosoftMailClient mailClient,
            ServiceConnectionProvider connectionProvider) {
        this.mailClient = mailClient;
        this.connectionProvider = connectionProvider;
    }

    MicrosoftEmailPlatform(MicrosoftMailClient mailClient) {
        this(mailClient, null);
    }

    @Override
    public String id() { return "microsoft"; }

    @Override
    public List<Mailbox> listMailboxes() {
        return TokenRetry.withTokenRetry(this::resolveToken,
                token -> mailClient.listMailFolders(token).stream()
                        .map(MailMapper::toMailbox).toList());
    }

    @Override
    public Page<EmailSummary> listMessages(String mailboxId, Instant from,
            Instant to, PageRequest pagination) {
        return TokenRetry.withTokenRetry(this::resolveToken,
                token -> {
                    var page = mailClient.listMessages(mailboxId, token,
                            from, to, pagination.pageSize(), pagination.cursor());
                    return new Page<>(page.items().stream()
                            .map(json -> MailMapper.toEmailSummary(json, mailboxId)).toList(),
                            page.nextCursor(), page.hasMore());
                });
    }

    @Override
    public EmailMessage getMessage(String mailboxId, String messageId) {
        return TokenRetry.withTokenRetry(this::resolveToken,
                token -> MailMapper.toEmailMessage(
                        mailClient.getMessage(messageId, token), mailboxId));
    }

    @Override
    public byte[] getAttachmentContent(String mailboxId, String messageId,
            String attachmentId) {
        return TokenRetry.withTokenRetry(this::resolveToken,
                token -> mailClient.getAttachmentContent(messageId, attachmentId, token));
    }

    @Override
    public Page<EmailSummary> search(String query, PageRequest pagination) {
        return TokenRetry.withTokenRetry(this::resolveToken,
                token -> {
                    var page = mailClient.searchMessages(query, token,
                            pagination.pageSize(), pagination.cursor());
                    return new Page<>(page.items().stream()
                            .map(json -> MailMapper.toEmailSummary(json, "search")).toList(),
                            page.nextCursor(), page.hasMore());
                });
    }

    private String resolveToken() {
        if (connectionProvider == null) return "test-token";
        return connectionProvider.getAccessToken(null, "microsoft", "default")
                .accessToken();
    }
}
