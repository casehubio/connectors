package io.casehub.connectors.graphql;

import io.casehub.connectors.email.spi.EmailPlatformService;
import io.casehub.connectors.email.model.EmailMessage;
import io.casehub.connectors.email.model.EmailSummary;
import io.casehub.connectors.email.model.Mailbox;
import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.email.spi.EmailPlatform;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

@McpDomain(value = "connectors/email", app = "connectors", basePath = "/api/connectors/email", summary = "Email connector — send, receive, search messages")
@ApplicationScoped
public class ConnectorEmailApi {

    @Inject EmailPlatformService emailService;

    @PlatformQuery("List mailboxes on a platform")
    @RestPath("/mailboxes")
    public List<Mailbox> listMailboxes(@QueryParam("platform") String platform) {
        EmailPlatform p = emailService.platform(platform);
        if (p == null) return List.of();
        return p.listMailboxes();
    }

    @PlatformQuery("List email messages in a mailbox")
    @RestPath("/mailboxes/{mailboxId}/messages")
    public Page<EmailSummary> listMessages(
            @QueryParam("platform") String platform,
            @PathParam String mailboxId,
            @QueryParam("from") Instant from,
            @QueryParam("to") Instant to,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        EmailPlatform p = emailService.platform(platform);
        if (p == null) return new Page<>(List.of(), null, false);
        Instant effectiveFrom = from != null ? from : Instant.now().minusSeconds(2592000);
        Instant effectiveTo = to != null ? to : Instant.now();
        int size = pageSize != null ? pageSize : 50;
        return p.listMessages(mailboxId, effectiveFrom, effectiveTo,
            new PageRequest(cursor, size));
    }

    @PlatformQuery("Get a full email message with body and attachments")
    @RestPath("/mailboxes/{mailboxId}/messages/{messageId}")
    public EmailMessage getMessage(
            @QueryParam("platform") String platform,
            @PathParam String mailboxId,
            @PathParam String messageId) {
        EmailPlatform p = emailService.platform(platform);
        if (p == null) return null;
        return p.getMessage(mailboxId, messageId);
    }

    @PlatformQuery("Get email attachment content as base64")
    @RestPath("/mailboxes/{mailboxId}/messages/{messageId}/attachments/{attachmentId}")
    public String getAttachmentContent(
            @QueryParam("platform") String platform,
            @PathParam String mailboxId,
            @PathParam String messageId,
            @PathParam String attachmentId) {
        EmailPlatform p = emailService.platform(platform);
        if (p == null) return null;
        byte[] content = p.getAttachmentContent(mailboxId, messageId, attachmentId);
        return content != null ? Base64.getEncoder().encodeToString(content) : null;
    }
}
