package io.casehub.connectors.chat.teams;

import io.casehub.connectors.microsoft.GraphErrorHandler;
import io.casehub.connectors.microsoft.MicrosoftGraphClient;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

class TeamsClient {

    private static final int SIMPLE_UPLOAD_LIMIT = 4 * 1024 * 1024;
    private static final int CHUNK_SIZE          = 16 * 320 * 1024;

    private final MicrosoftGraphClient graph;

    TeamsClient(MicrosoftGraphClient graph) {
        this.graph = graph;
    }

    List<JsonObject> listJoinedTeams(String token) {
        return graph.getPage("/me/joinedTeams", token, Function.identity(), null).items();
    }

    List<JsonObject> listChannels(String teamId, String token) {
        return graph.getPage("/teams/" + teamId + "/channels", token,
                Function.identity(), null).items();
    }

    JsonObject sendMessage(String teamId, String channelId, JsonObject body, String token) {
        return graph.post("/teams/" + teamId + "/channels/" + channelId + "/messages",
                body, token);
    }

    JsonObject replyToMessage(String teamId, String channelId, String messageId,
            JsonObject body, String token) {
        return graph.post("/teams/" + teamId + "/channels/" + channelId
                + "/messages/" + messageId + "/replies", body, token);
    }

    JsonObject getPresence(String userId, String token) {
        return graph.get("/users/" + userId + "/presence", token);
    }

    List<JsonObject> listMembers(String teamId, String token) {
        List<JsonObject> all = new ArrayList<>();
        String cursor = null;
        do {
            var page = graph.getPage("/teams/" + teamId + "/members", token,
                    Function.identity(), cursor);
            all.addAll(page.items());
            cursor = page.hasMore() ? page.nextCursor() : null;
        } while (cursor != null);
        return List.copyOf(all);
    }

    JsonObject createChannel(String teamId, JsonObject body, String token) {
        return graph.post("/teams/" + teamId + "/channels", body, token);
    }

    void deleteChannel(String teamId, String channelId, String token) {
        graph.delete("/teams/" + teamId + "/channels/" + channelId, token);
    }

    JsonObject getChannel(String teamId, String channelId, String token) {
        return graph.get("/teams/" + teamId + "/channels/" + channelId, token);
    }

    List<JsonObject> listMessages(String teamId, String channelId, String token) {
        List<JsonObject> all = new ArrayList<>();
        String cursor = null;
        do {
            var page = graph.getPage("/teams/" + teamId + "/channels/" + channelId
                    + "/messages?$top=50", token, Function.identity(), cursor);
            all.addAll(page.items());
            cursor = page.hasMore() ? page.nextCursor() : null;
        } while (cursor != null);
        return List.copyOf(all);
    }

    void addMember(String teamId, String userId, String token) {
        var body = Json.createObjectBuilder()
                .add("@odata.type", "#microsoft.graph.aadUserConversationMember")
                .add("roles", Json.createArrayBuilder())
                .add("user@odata.bind",
                        MicrosoftGraphClient.BASE_URL + "/users/" + userId)
                .build();
        graph.post("/teams/" + teamId + "/members", body, token);
    }

    void removeMember(String teamId, String userId, String token) {
        var members = listMembers(teamId, token);
        for (var member : members) {
            if (userId.equals(member.getString("userId", ""))) {
                graph.delete("/teams/" + teamId + "/members/" + member.getString("id"),
                        token);
                return;
            }
        }
    }

    JsonObject getChannelFilesFolder(String teamId, String channelId, String token) {
        return graph.get("/teams/" + teamId + "/channels/" + channelId + "/filesFolder", token);
    }

    JsonObject uploadToChannelFolder(String driveId, String folderId, String filename,
                                     String contentType, byte[] content, String token) {
        String encodedName = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        if (content.length <= SIMPLE_UPLOAD_LIMIT) {
            return uploadSimple(driveId, folderId, encodedName, contentType, content, token);
        }
        return uploadResumable(driveId, folderId, filename, encodedName, contentType, content, token);
    }

    private JsonObject uploadSimple(String driveId, String folderId, String encodedName,
                                    String contentType, byte[] content, String token) {
        var request = HttpRequest.newBuilder(
                                         URI.create(MicrosoftGraphClient.BASE_URL
                                                    + "/drives/" + driveId + "/items/" + folderId
                                                    + ":/" + encodedName + ":/content"))
                                 .header("Authorization", "Bearer " + token)
                                 .header("Content-Type", contentType)
                                 .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                                 .build();
        var response = graph.send(request);
        GraphErrorHandler.handle(response, "microsoft");
        return parseJsonObject(response.body());
    }

    private JsonObject uploadResumable(String driveId, String folderId,
                                       String filename, String encodedName,
                                       String contentType, byte[] content, String token) {
        var sessionBody = Json.createObjectBuilder()
                              .add("item", Json.createObjectBuilder()
                                               .add("@microsoft.graph.conflictBehavior", "replace")
                                               .add("name", filename))
                              .build();
        var session = graph.post("/drives/" + driveId + "/items/" + folderId
                                 + ":/" + encodedName + ":/createUploadSession", sessionBody, token);
        String uploadUrl = session.getString("uploadUrl");

        int        totalSize = content.length;
        int        offset    = 0;
        JsonObject result    = null;
        while (offset < totalSize) {
            int    end   = Math.min(offset + CHUNK_SIZE, totalSize);
            byte[] chunk = Arrays.copyOfRange(content, offset, end);
            var request = HttpRequest.newBuilder(URI.create(uploadUrl))
                                     .header("Content-Type", contentType)
                                     .header("Content-Range",
                                             "bytes " + offset + "-" + (end - 1) + "/" + totalSize)
                                     .PUT(HttpRequest.BodyPublishers.ofByteArray(chunk))
                                     .build();
            var response = graph.send(request);
            if (response.statusCode() >= 300) {
                GraphErrorHandler.handle(response, "microsoft");
            }
            if (end >= totalSize) {
                result = parseJsonObject(response.body());
            }
            offset = end;
        }
        return result;
    }

    private static JsonObject parseJsonObject(String body) {
        try (var reader = Json.createReader(new StringReader(body))) {
            return reader.readObject();
        }
    }

}
