package io.casehub.connectors.email.microsoft;

import io.casehub.connectors.email.model.EmailAttachment;
import io.casehub.connectors.email.model.EmailMessage;
import io.casehub.connectors.email.model.EmailSummary;
import io.casehub.connectors.email.model.Mailbox;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class MailMapper {

    private MailMapper() {}

    static Mailbox toMailbox(JsonObject json) {
        return new Mailbox(
                json.getString("id"),
                json.getString("displayName", ""),
                json.getInt("unreadItemCount", 0));
    }

    static EmailSummary toEmailSummary(JsonObject json, String mailboxId) {
        return new EmailSummary(
                json.getString("id"),
                mailboxId,
                json.getString("id"),
                extractEmailAddress(json.getJsonObject("from")),
                json.getString("subject", ""),
                parseInstant(json.getString("receivedDateTime", null)),
                json.getBoolean("isRead", false));
    }

    static EmailMessage toEmailMessage(JsonObject json, String mailboxId) {
        return new EmailMessage(
                json.getString("id"),
                mailboxId,
                json.getString("id"),
                extractEmailAddress(json.getJsonObject("from")),
                extractRecipients(json.getJsonArray("toRecipients")),
                extractRecipients(json.getJsonArray("ccRecipients")),
                json.getString("subject", ""),
                extractBodyText(json),
                extractBodyHtml(json),
                parseInstant(json.getString("receivedDateTime", null)),
                json.getBoolean("isRead", false),
                extractAttachments(json));
    }

    private static String extractEmailAddress(JsonObject from) {
        if (from == null) return "";
        JsonObject addr = from.getJsonObject("emailAddress");
        if (addr == null) return "";
        return addr.getString("address", "");
    }

    private static List<String> extractRecipients(JsonArray recipients) {
        if (recipients == null) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonValue v : recipients) {
            JsonObject r = v.asJsonObject();
            JsonObject addr = r.getJsonObject("emailAddress");
            if (addr != null) {
                result.add(addr.getString("address", ""));
            }
        }
        return List.copyOf(result);
    }

    private static String extractBodyText(JsonObject json) {
        JsonObject body = json.getJsonObject("body");
        if (body == null) return "";
        if ("text".equals(body.getString("contentType", ""))) {
            return body.getString("content", "");
        }
        return "";
    }

    private static String extractBodyHtml(JsonObject json) {
        JsonObject body = json.getJsonObject("body");
        if (body == null) return "";
        if ("html".equals(body.getString("contentType", ""))) {
            return body.getString("content", "");
        }
        return "";
    }

    private static List<EmailAttachment> extractAttachments(JsonObject json) {
        JsonArray attachments = json.getJsonArray("attachments");
        if (attachments == null) return List.of();
        List<EmailAttachment> result = new ArrayList<>();
        for (JsonValue v : attachments) {
            JsonObject att = v.asJsonObject();
            result.add(new EmailAttachment(
                    att.getString("id"),
                    att.getString("name", ""),
                    att.getString("contentType", ""),
                    att.getJsonNumber("size") != null ? att.getJsonNumber("size").longValue() : 0L));
        }
        return List.copyOf(result);
    }

    private static Instant parseInstant(String dateTime) {
        if (dateTime == null || dateTime.isBlank()) return null;
        try {
            if (!dateTime.endsWith("Z") && !dateTime.contains("+")) {
                dateTime = dateTime + "Z";
            }
            return DateTimeFormatter.ISO_INSTANT.parse(dateTime, Instant::from);
        } catch (Exception e) {
            return null;
        }
    }
}
