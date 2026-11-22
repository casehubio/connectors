package io.casehub.connectors.chat.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.HttpMethod;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;
import io.casehub.connectors.telegram.bot.TelegramBotClient;

class TelegramInboundConnectorTest {

    private TelegramInboundConnector connector;
    private static final String SECRET = "my-secret-token";

    @BeforeEach
    void setUp() {
        connector = new TelegramInboundConnector(SECRET, null);
    }

    @Test
    void id_returns_telegram_inbound() {
        assertThat(connector.id()).isEqualTo(InboundConnectorIds.TELEGRAM_INBOUND);
    }

    @Test
    void rejects_missing_secret_token() {
        WebhookRequest request = new WebhookRequest(
                "{\"update_id\":1,\"message\":{\"message_id\":1,\"chat\":{\"id\":100,\"type\":\"private\"},\"from\":{\"id\":42,\"first_name\":\"Alice\",\"is_bot\":false},\"text\":\"Hello\",\"date\":1700000000}}",
                Map.of(), Map.of(), HttpMethod.POST, "/connectors/telegram-inbound/webhook");

        WebhookResult result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Unauthorized.class);
    }

    @Test
    void rejects_wrong_secret_token() {
        WebhookRequest request = new WebhookRequest(
                "{\"update_id\":1,\"message\":{\"message_id\":1,\"chat\":{\"id\":100,\"type\":\"private\"},\"from\":{\"id\":42,\"first_name\":\"Alice\",\"is_bot\":false},\"text\":\"Hello\",\"date\":1700000000}}",
                Map.of("x-telegram-bot-api-secret-token", List.of("wrong-secret")),
                Map.of(), HttpMethod.POST, "/connectors/telegram-inbound/webhook");

        WebhookResult result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Unauthorized.class);
    }

    @Test
    void delivers_valid_message() {
        WebhookRequest request = new WebhookRequest(
                "{\"update_id\":1,\"message\":{\"message_id\":42,\"chat\":{\"id\":100,\"type\":\"supergroup\",\"title\":\"Dev\"},\"from\":{\"id\":55,\"first_name\":\"Alice\",\"is_bot\":false},\"text\":\"Hello bot\",\"date\":1700000000}}",
                Map.of("x-telegram-bot-api-secret-token", List.of(SECRET)),
                Map.of(), HttpMethod.POST, "/connectors/telegram-inbound/webhook");

        WebhookResult result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        assertThat(delivered.messages()).hasSize(1);
        var msg = delivered.messages().get(0);
        assertThat(msg.connectorId()).isEqualTo(InboundConnectorIds.TELEGRAM_INBOUND);
        assertThat(msg.connectorType()).isEqualTo("telegram");
        assertThat(msg.externalSenderId()).isEqualTo("55");
        assertThat(msg.externalChannelRef()).isEqualTo("100");
        assertThat(msg.content()).isEqualTo("Hello bot");
        assertThat(msg.metadata().get("telegram-chat-type")).isEqualTo("supergroup");
        assertThat(msg.metadata().get("telegram-message-id")).isEqualTo("42");
    }

    @Test
    void ignores_update_without_message() {
        WebhookRequest request = new WebhookRequest(
                "{\"update_id\":1}",
                Map.of("x-telegram-bot-api-secret-token", List.of(SECRET)),
                Map.of(), HttpMethod.POST, "/connectors/telegram-inbound/webhook");

        WebhookResult result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Ignored.class);
    }

    @Test
    void extracts_reply_metadata() {
        WebhookRequest request = new WebhookRequest(
                "{\"update_id\":1,\"message\":{\"message_id\":43,\"chat\":{\"id\":100,\"type\":\"group\"},\"from\":{\"id\":55,\"first_name\":\"Alice\",\"is_bot\":false},\"text\":\"Reply\",\"date\":1700000000,\"reply_to_message\":{\"message_id\":42,\"chat\":{\"id\":100,\"type\":\"group\"},\"from\":{\"id\":1,\"first_name\":\"Bot\",\"is_bot\":true},\"text\":\"Original\",\"date\":1699999000}}}",
                Map.of("x-telegram-bot-api-secret-token", List.of(SECRET)),
                Map.of(), HttpMethod.POST, "/connectors/telegram-inbound/webhook");

        WebhookResult result = connector.handle(request);

        var msg = ((WebhookResult.Delivered) result).messages().get(0);
        assertThat(msg.metadata().get("telegram-reply-to-message-id")).isEqualTo("42");
    }

    @Test
    void delivers_edited_message() {
        WebhookRequest request = new WebhookRequest(
                "{\"update_id\":1,\"edited_message\":{\"message_id\":42,\"chat\":{\"id\":100,\"type\":\"private\"},\"from\":{\"id\":55,\"first_name\":\"Alice\",\"is_bot\":false},\"text\":\"Edited text\",\"date\":1700000000}}",
                Map.of("x-telegram-bot-api-secret-token", List.of(SECRET)),
                Map.of(), HttpMethod.POST, "/connectors/telegram-inbound/webhook");

        WebhookResult result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var msg = ((WebhookResult.Delivered) result).messages().get(0);
        assertThat(msg.content()).isEqualTo("Edited text");
        assertThat(msg.metadata().get("telegram-message-id")).isEqualTo("42");
    }

    @Test
    void delivers_callback_query_message() {
        WebhookRequest request = new WebhookRequest(
                "{\"update_id\":1,\"callback_query\":{\"id\":\"cb1\",\"from\":{\"id\":55,\"first_name\":\"Alice\",\"is_bot\":false},\"message\":{\"message_id\":42,\"chat\":{\"id\":100,\"type\":\"private\"},\"from\":{\"id\":1,\"first_name\":\"Bot\",\"is_bot\":true},\"text\":\"Button message\",\"date\":1700000000},\"data\":\"button_1\"}}",
                Map.of("x-telegram-bot-api-secret-token", List.of(SECRET)),
                Map.of(), HttpMethod.POST, "/connectors/telegram-inbound/webhook");

        WebhookResult result = connector.handle(request);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var msg = ((WebhookResult.Delivered) result).messages().get(0);
        assertThat(msg.content()).isEqualTo("Button message");
    }

    @Test
    void registers_known_chat_on_delivery() {
        var platform = new TelegramChatPlatform(
                new TelegramBotClient("http://localhost:1"),
                java.util.Optional.of("token"), java.util.Optional.empty());
        platform.init();
        var connectorWithPlatform = new TelegramInboundConnector(SECRET, platform);

        WebhookRequest request = new WebhookRequest(
                "{\"update_id\":1,\"message\":{\"message_id\":42,\"chat\":{\"id\":200,\"type\":\"supergroup\",\"title\":\"Test Group\"},\"from\":{\"id\":55,\"first_name\":\"Alice\",\"is_bot\":false},\"text\":\"Hello\",\"date\":1700000000}}",
                Map.of("x-telegram-bot-api-secret-token", List.of(SECRET)),
                Map.of(), HttpMethod.POST, "/connectors/telegram-inbound/webhook");

        connectorWithPlatform.handle(request);

        assertThat(platform.knownChatIds()).contains("200");
    }
}
