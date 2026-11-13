package io.casehub.connectors.chat.teams;

import io.casehub.connectors.microsoft.MicrosoftGraphClient;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

class TeamsClient {

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
}
