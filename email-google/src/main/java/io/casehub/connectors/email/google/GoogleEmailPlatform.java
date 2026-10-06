package io.casehub.connectors.email.google;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;

import org.jboss.logging.Logger;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.email.model.EmailMessage;
import io.casehub.connectors.email.model.EmailSummary;
import io.casehub.connectors.email.model.Mailbox;
import io.casehub.connectors.email.spi.EmailPlatform;

public class GoogleEmailPlatform implements EmailPlatform {

    private static final Logger LOG = Logger.getLogger(GoogleEmailPlatform.class);
    private static final String USER_ID = "me";
    private static final DateTimeFormatter GMAIL_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Set<String> EXCLUDED_LABELS = Set.of(
            "TRASH", "SPAM", "STARRED", "IMPORTANT", "UNREAD",
            "CATEGORY_SOCIAL", "CATEGORY_UPDATES", "CATEGORY_FORUMS",
            "CATEGORY_PROMOTIONS", "CATEGORY_PERSONAL");


    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private Gmail gmailService;

    public GoogleEmailPlatform(String clientId, String clientSecret, String refreshToken) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        init();
    }

    GoogleEmailPlatform(Gmail gmailService) {
        this.clientId = "";
        this.clientSecret = "";
        this.refreshToken = "";
        this.gmailService = gmailService;
    }

    void init() {
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
            LOG.warn("Gmail credentials not configured — platform inactive");
            return;
        }
        try {
            var credentials = UserCredentials.newBuilder()
                    .setClientId(clientId)
                    .setClientSecret(clientSecret)
                    .setRefreshToken(refreshToken)
                    .build();
            gmailService = new Gmail.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("casehub-connectors")
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            LOG.errorf(e, "Failed to initialize Gmail client");
        }
    }

    @Override
    public String id() {
        return "google";
    }

    @Override
    public List<Mailbox> listMailboxes() {
        requireClient();
        try {
            var response = gmailService.users().labels().list(USER_ID).execute();
            if (response.getLabels() == null) {
                return List.of();
            }
            return response.getLabels().stream()
                    .filter(label -> !EXCLUDED_LABELS.contains(label.getId()))
                    .map(label -> new Mailbox(
                            label.getId(),
                            friendlyName(label.getId(), label.getName()),
                            label.getMessagesUnread() != null ? label.getMessagesUnread() : 0))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list mailboxes", e);
        }
    }

    @Override
    public Page<EmailSummary> listMessages(String mailboxId, Instant from,
                                           Instant to, PageRequest pagination) {
        requireClient();
        try {
            String query = buildDateQuery(from, to);
            var request = gmailService.users().messages().list(USER_ID)
                    .setLabelIds(List.of(mailboxId))
                    .setQ(query)
                    .setMaxResults((long) pagination.pageSize());
            if (pagination.cursor() != null) {
                request.setPageToken(pagination.cursor());
            }

            var response = request.execute();
            if (response.getMessages() == null) {
                return Page.of(List.of());
            }

            List<EmailSummary> summaries = new ArrayList<>();
            for (var msgRef : response.getMessages()) {
                try {
                    var full = gmailService.users().messages().get(USER_ID, msgRef.getId())
                            .setFormat("metadata")
                            .setMetadataHeaders(List.of("From", "Subject", "Date", "Message-ID"))
                            .execute();
                    summaries.add(GmailMessageMapper.toEmailSummary(full, mailboxId));
                } catch (IOException e) {
                    LOG.warnf(e, "Failed to fetch metadata for message '%s' — skipping", msgRef.getId());
                }
            }

            String nextPageToken = response.getNextPageToken();
            boolean hasMore = nextPageToken != null;
            return new Page<>(summaries, nextPageToken, hasMore);
        } catch (IOException e) {
            throw new RuntimeException("Failed to list messages", e);
        }
    }

    @Override
    public EmailMessage getMessage(String mailboxId, String messageId) {
        requireClient();
        try {
            var message = gmailService.users().messages().get(USER_ID, messageId)
                    .setFormat("full")
                    .execute();
            return GmailMessageMapper.toEmailMessage(message, mailboxId);
        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                throw new NoSuchElementException(
                        "Message '" + messageId + "' not found in mailbox '" + mailboxId + "'");
            }
            throw new RuntimeException("Failed to get message " + messageId, e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to get message " + messageId, e);
        }
    }

    @Override
    public byte[] getAttachmentContent(String mailboxId, String messageId,
                                       String attachmentId) {
        requireClient();
        try {
            var attachment = gmailService.users().messages().attachments()
                    .get(USER_ID, messageId, attachmentId)
                    .execute();
            return Base64.getUrlDecoder().decode(attachment.getData());
        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                throw new NoSuchElementException(
                        "Attachment '" + attachmentId + "' not found on message '"
                        + messageId + "' in mailbox '" + mailboxId + "'");
            }
            throw new RuntimeException("Failed to get attachment " + attachmentId, e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to get attachment " + attachmentId, e);
        }
    }

    @Override
    public Page<EmailSummary> search(String query, PageRequest pagination) {
        requireClient();
        try {
            var request = gmailService.users().messages().list(USER_ID)
                                      .setQ(query)
                                      .setMaxResults((long) pagination.pageSize());
            if (pagination.cursor() != null) {
                request.setPageToken(pagination.cursor());
            }

            var response = request.execute();
            if (response.getMessages() == null) {
                return Page.of(List.of());
            }

            List<EmailSummary> summaries = new ArrayList<>();
            for (var msgRef : response.getMessages()) {
                try {
                    var full = gmailService.users().messages().get(USER_ID, msgRef.getId())
                                           .setFormat("metadata")
                                           .setMetadataHeaders(List.of("From", "Subject", "Date", "Message-ID"))
                                           .execute();
                    String mailboxId = GmailMessageMapper.primaryLabel(full);
                    summaries.add(GmailMessageMapper.toEmailSummary(full, mailboxId));
                } catch (IOException e) {
                    LOG.warnf(e, "Failed to fetch metadata for message '%s' — skipping", msgRef.getId());
                }
            }

            String  nextPageToken = response.getNextPageToken();
            boolean hasMore       = nextPageToken != null;
            return new Page<>(summaries, nextPageToken, hasMore);
        } catch (IOException e) {
            throw new RuntimeException("Failed to search messages", e);
        }
    }


    boolean isActive() {
        return gmailService != null;
    }

    private void requireClient() {
        if (gmailService == null) {
            throw new IllegalStateException(
                    "Gmail client not initialised — check credentials configuration");
        }
    }

    private static String buildDateQuery(Instant from, Instant to) {
        String afterDate = from.atOffset(ZoneOffset.UTC).format(GMAIL_DATE);
        String beforeDate = to.atOffset(ZoneOffset.UTC).format(GMAIL_DATE);
        return "after:" + afterDate + " before:" + beforeDate;
    }

    private static String friendlyName(String id, String name) {
        return switch (id) {
            case "INBOX" -> "Inbox";
            case "SENT" -> "Sent";
            case "DRAFT" -> "Drafts";
            default -> name;
        };
    }
}
