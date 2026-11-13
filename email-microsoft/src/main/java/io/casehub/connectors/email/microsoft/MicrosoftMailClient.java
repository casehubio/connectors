package io.casehub.connectors.email.microsoft;

import io.casehub.connectors.Page;
import io.casehub.connectors.microsoft.MicrosoftGraphClient;

import jakarta.json.JsonObject;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

class MicrosoftMailClient {

    private final MicrosoftGraphClient graph;

    MicrosoftMailClient(MicrosoftGraphClient graph) {
        this.graph = graph;
    }

    List<JsonObject> listMailFolders(String accessToken) {
        return graph.getPage("/me/mailFolders", accessToken,
                json -> json, null).items();
    }

    Page<JsonObject> listMessages(String folderId, String accessToken,
            Instant from, Instant to, int top, String cursor) {
        if (cursor != null) {
            return graph.getPage("/me/mailFolders/" + folderId + "/messages",
                    accessToken, json -> json, cursor);
        }
        String filter = buildDateFilter(from, to);
        String path = "/me/mailFolders/" + folderId + "/messages?$top=" + top
                + (filter.isEmpty() ? "" : "&$filter=" + filter)
                + "&$orderby=receivedDateTime desc";
        return graph.getPage(path, accessToken, json -> json, null);
    }

    JsonObject getMessage(String messageId, String accessToken) {
        return graph.get("/me/messages/" + messageId
                + "?$expand=attachments", accessToken);
    }

    byte[] getAttachmentContent(String messageId, String attachmentId,
            String accessToken) {
        return graph.getBytes("/me/messages/" + messageId
                + "/attachments/" + attachmentId + "/$value", accessToken);
    }

    Page<JsonObject> searchMessages(String query, String accessToken,
            int top, String cursor) {
        if (cursor != null) {
            return graph.getPage("/me/messages", accessToken, json -> json, cursor);
        }
        String path = "/me/messages?$search=\"" + query + "\"&$top=" + top;
        return graph.getPage(path, accessToken, json -> json, null);
    }

    private static String buildDateFilter(Instant from, Instant to) {
        var parts = new ArrayList<String>();
        if (from != null) {
            parts.add("receivedDateTime ge " + DateTimeFormatter.ISO_INSTANT.format(from));
        }
        if (to != null) {
            parts.add("receivedDateTime lt " + DateTimeFormatter.ISO_INSTANT.format(to));
        }
        return String.join(" and ", parts);
    }
}
