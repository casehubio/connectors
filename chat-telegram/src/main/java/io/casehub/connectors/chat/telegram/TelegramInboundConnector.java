package io.casehub.connectors.chat.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.WebhookInboundConnector;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;
import io.casehub.connectors.telegram.bot.model.TelegramMessage;
import io.casehub.connectors.telegram.bot.model.TelegramUpdate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TelegramInboundConnector extends WebhookInboundConnector {

    private static final Logger LOG = Logger.getLogger(TelegramInboundConnector.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String secretToken;
    private final TelegramChatPlatform chatPlatform;

    public TelegramInboundConnector(String secretToken, TelegramChatPlatform chatPlatform) {
        this.secretToken = secretToken;
        this.chatPlatform = chatPlatform;
    }

    @Override
    public String id() {
        return InboundConnectorIds.TELEGRAM_INBOUND;
    }

    @Override
    public WebhookResult handle(final WebhookRequest request) {
        try {
            final String headerToken = request.header("x-telegram-bot-api-secret-token");
            if (!secretToken.isEmpty() && (headerToken == null || !MessageDigest.isEqual(
                    secretToken.getBytes(StandardCharsets.UTF_8),
                    headerToken.getBytes(StandardCharsets.UTF_8)))) {
                LOG.warning("telegram-inbound: invalid secret token");
                return new WebhookResult.Unauthorized();
            }

            final TelegramUpdate update = MAPPER.readValue(request.body(), TelegramUpdate.class);
            final TelegramMessage message = extractMessage(update);
            if (message == null || message.text() == null) {
                return new WebhookResult.Ignored();
            }

            if (chatPlatform != null && message.chat() != null) {
                chatPlatform.registerKnownChat(message.chat());
            }

            final Map<String, String> metadata = new HashMap<>();
            metadata.put("telegram-chat-id", String.valueOf(message.chat().id()));
            metadata.put("telegram-message-id", String.valueOf(message.messageId()));
            metadata.put("telegram-sender-id", String.valueOf(message.from().id()));
            metadata.put("telegram-chat-type", message.chat().type());
            if (message.replyToMessage() != null) {
                metadata.put("telegram-reply-to-message-id",
                        String.valueOf(message.replyToMessage().messageId()));
            }

            final InboundMessage inbound = new InboundMessage(
                    InboundConnectorIds.TELEGRAM_INBOUND,
                    InboundConnectorTypes.TELEGRAM,
                    String.valueOf(message.from().id()),
                    String.valueOf(message.chat().id()),
                    message.text(),
                    List.of(),
                    Instant.now(),
                    metadata,
                    null);

            return new WebhookResult.Delivered(List.of(inbound));
        } catch (final Exception e) {
            LOG.log(Level.SEVERE, "telegram-inbound: error handling webhook", e);
            return new WebhookResult.Ignored();
        }
    }

    private static TelegramMessage extractMessage(final TelegramUpdate update) {
        if (update.message() != null) return update.message();
        if (update.editedMessage() != null) return update.editedMessage();
        if (update.callbackQuery() != null && update.callbackQuery().message() != null) {
            return update.callbackQuery().message();
        }
        return null;
    }
}
