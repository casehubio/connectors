package io.casehub.connectors.chat.teams;

import jakarta.json.JsonObject;
import java.util.Map;

public record InvokeRequest(
        String name,
        JsonObject value,
        String senderId,
        String channelRef,
        Map<String, String> metadata) {}
