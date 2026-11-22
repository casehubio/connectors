package io.casehub.connectors.telegram.bot;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.connectors.telegram.bot.model.TelegramChat;
import io.casehub.connectors.telegram.bot.model.TelegramMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;


@EnabledIfEnvironmentVariable(named = "CASEHUB_TELEGRAM_BOT_TOKEN", matches = ".+")
class TelegramBotClientLiveTest {

    private static TelegramBotClient client;
    private static String token;
    private static String chatId;

    @BeforeAll
    static void setUp() {
        token = System.getenv("CASEHUB_TELEGRAM_BOT_TOKEN");
        chatId = System.getenv("CASEHUB_TELEGRAM_CHAT_ID");
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalStateException("Set CASEHUB_TELEGRAM_CHAT_ID to run live tests");
        }
        client = new TelegramBotClient("https://api.telegram.org");
    }

    @Test
    void sendMessage_delivers_to_real_chat() {
        TelegramMessage msg = client.sendMessage(token, chatId,
                "Hello from TelegramBotClient! This is a live integration test.", null);

        assertThat(msg).isNotNull();
        assertThat(msg.messageId()).isPositive();
        assertThat(msg.chat().id()).isEqualTo(Long.parseLong(chatId));
        System.out.println("Sent message ID: " + msg.messageId());
    }

    @Test
    void getChat_returns_chat_info() {
        TelegramChat chat = client.getChat(token, chatId);

        assertThat(chat).isNotNull();
        assertThat(chat.type()).isEqualTo("private");
        System.out.println("Chat: " + chat.firstName() + " " + chat.lastName()
                + " (@" + chat.username() + ")");
    }

    @Test
    void setMessageReaction_on_sent_message() {
        TelegramMessage msg = client.sendMessage(token, chatId,
                "React to this message! 👍", null);
        assertThat(msg).isNotNull();

        boolean reacted = client.setMessageReaction(token, chatId, msg.messageId(), "👍");
        assertThat(reacted).isTrue();
        System.out.println("Reacted to message " + msg.messageId() + " with 👍");
    }

    @Test
    void sendMessage_with_reply() {
        TelegramMessage original = client.sendMessage(token, chatId,
                "Original message", null);
        assertThat(original).isNotNull();

        TelegramMessage reply = client.sendMessage(token, chatId,
                "This is a threaded reply!", original.messageId());
        assertThat(reply).isNotNull();
        System.out.println("Reply " + reply.messageId() + " to " + original.messageId());
    }
}
