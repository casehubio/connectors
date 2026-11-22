package io.casehub.connectors.telegram.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.connectors.http.HttpHelper;
import io.casehub.connectors.telegram.bot.model.TelegramChat;
import io.casehub.connectors.telegram.bot.model.TelegramChatMember;
import io.casehub.connectors.telegram.bot.model.TelegramMessage;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TelegramBotClient {

    private static final Logger LOG = Logger.getLogger(TelegramBotClient.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;

    @Inject
    public TelegramBotClient(
            @ConfigProperty(name = "casehub.telegram.api-url",
                            defaultValue = "https://api.telegram.org")
            final String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public record BotCommand(String command, String description) {}

    public TelegramMessage sendMessage(final String token, final String chatId,
                                       final String text, final Long replyToMessageId) {
        try {
            final ObjectNode body = MAPPER.createObjectNode();
            body.put("chat_id", chatId);
            body.put("text", text);
            if (replyToMessageId != null) {
                body.put("reply_to_message_id", replyToMessageId);
            }
            final JsonNode result = callMethod(token, "sendMessage", body);
            return result != null ? MAPPER.treeToValue(result, TelegramMessage.class) : null;
        } catch (final Exception e) {
            LOG.log(Level.WARNING, "telegram sendMessage error", e);
            return null;
        }
    }

    public boolean setMessageReaction(final String token, final String chatId,
                                      final long messageId, final String emoji) {
        try {
            final ObjectNode body = MAPPER.createObjectNode();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            final ArrayNode reaction = body.putArray("reaction");
            if (emoji != null && !emoji.isEmpty()) {
                final ObjectNode r = reaction.addObject();
                r.put("type", "emoji");
                r.put("emoji", emoji);
            }
            return callMethod(token, "setMessageReaction", body) != null;
        } catch (final Exception e) {
            LOG.log(Level.WARNING, "telegram setMessageReaction error", e);
            return false;
        }
    }

    public TelegramChat getChat(final String token, final String chatId) {
        try {
            final ObjectNode body = MAPPER.createObjectNode();
            body.put("chat_id", chatId);
            final JsonNode result = callMethod(token, "getChat", body);
            return result != null ? MAPPER.treeToValue(result, TelegramChat.class) : null;
        } catch (final Exception e) {
            LOG.log(Level.WARNING, "telegram getChat error", e);
            return null;
        }
    }

    public int getChatMemberCount(final String token, final String chatId) {
        try {
            final ObjectNode body = MAPPER.createObjectNode();
            body.put("chat_id", chatId);
            final JsonNode result = callMethod(token, "getChatMemberCount", body);
            return result != null ? result.asInt(0) : 0;
        } catch (final Exception e) {
            LOG.log(Level.WARNING, "telegram getChatMemberCount error", e);
            return 0;
        }
    }

    public TelegramChatMember getChatMember(final String token, final String chatId,
                                             final long userId) {
        try {
            final ObjectNode body = MAPPER.createObjectNode();
            body.put("chat_id", chatId);
            body.put("user_id", userId);
            final JsonNode result = callMethod(token, "getChatMember", body);
            return result != null ? MAPPER.treeToValue(result, TelegramChatMember.class) : null;
        } catch (final Exception e) {
            LOG.log(Level.WARNING, "telegram getChatMember error", e);
            return null;
        }
    }

    public List<TelegramChatMember> getChatAdministrators(final String token, final String chatId) {
        try {
            final ObjectNode body = MAPPER.createObjectNode();
            body.put("chat_id", chatId);
            final JsonNode result = callMethod(token, "getChatAdministrators", body);
            if (result == null || !result.isArray()) return List.of();
            final List<TelegramChatMember> members = new ArrayList<>();
            for (final JsonNode node : result) {
                members.add(MAPPER.treeToValue(node, TelegramChatMember.class));
            }
            return List.copyOf(members);
        } catch (final Exception e) {
            LOG.log(Level.WARNING, "telegram getChatAdministrators error", e);
            return List.of();
        }
    }

    public boolean setMyCommands(final String token, final List<BotCommand> commands) {
        try {
            final ObjectNode body = MAPPER.createObjectNode();
            final ArrayNode cmds = body.putArray("commands");
            for (final BotCommand cmd : commands) {
                final ObjectNode c = cmds.addObject();
                c.put("command", cmd.command());
                c.put("description", cmd.description());
            }
            return callMethod(token, "setMyCommands", body) != null;
        } catch (final Exception e) {
            LOG.log(Level.WARNING, "telegram setMyCommands error", e);
            return false;
        }
    }

    public boolean deleteMyCommands(final String token) {
        try {
            return callMethod(token, "deleteMyCommands", MAPPER.createObjectNode()) != null;
        } catch (final Exception e) {
            LOG.log(Level.WARNING, "telegram deleteMyCommands error", e);
            return false;
        }
    }

    private JsonNode callMethod(final String token, final String method,
                                 final ObjectNode body) throws Exception {
        final HttpResponse<String> resp = HttpHelper.CLIENT.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/bot" + token + "/" + method))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                        .timeout(TIMEOUT)
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            LOG.warning("telegram " + method + " failed: HTTP " + resp.statusCode() + " — " + resp.body());
            return null;
        }
        final JsonNode json = MAPPER.readTree(resp.body());
        if (!json.path("ok").asBoolean(false)) {
            LOG.warning("telegram " + method + " not ok: " + resp.body());
            return null;
        }
        return json.get("result");
    }
}
