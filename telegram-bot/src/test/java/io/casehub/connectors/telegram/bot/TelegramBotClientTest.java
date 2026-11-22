package io.casehub.connectors.telegram.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.casehub.connectors.telegram.bot.model.TelegramChat;
import io.casehub.connectors.telegram.bot.model.TelegramChatMember;
import io.casehub.connectors.telegram.bot.model.TelegramMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TelegramBotClientTest {

    private WireMockServer wm;
    private TelegramBotClient client;
    private static final String TOKEN = "123456:ABC-DEF";

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wm.start();
        client = new TelegramBotClient("http://localhost:" + wm.port());
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    @Test
    void sendMessage_returns_message_with_id() {
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/sendMessage"))
                .willReturn(okJson("""
                    {"ok":true,"result":{"message_id":42,"chat":{"id":100,"type":"private"},"from":{"id":1,"first_name":"Bot","is_bot":true},"text":"Hello","date":1700000000}}
                    """)));

        TelegramMessage msg = client.sendMessage(TOKEN, "100", "Hello", null);

        assertThat(msg).isNotNull();
        assertThat(msg.messageId()).isEqualTo(42);
        assertThat(msg.text()).isEqualTo("Hello");
        wm.verify(postRequestedFor(urlPathEqualTo("/bot" + TOKEN + "/sendMessage"))
                .withRequestBody(matchingJsonPath("$.chat_id", equalTo("100")))
                .withRequestBody(matchingJsonPath("$.text", equalTo("Hello"))));
    }

    @Test
    void sendMessage_with_reply() {
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/sendMessage"))
                .willReturn(okJson("""
                    {"ok":true,"result":{"message_id":43,"chat":{"id":100,"type":"group"},"from":{"id":1,"first_name":"Bot","is_bot":true},"text":"Reply","date":1700000000}}
                    """)));

        TelegramMessage msg = client.sendMessage(TOKEN, "100", "Reply", 42L);

        assertThat(msg.messageId()).isEqualTo(43);
        wm.verify(postRequestedFor(urlPathEqualTo("/bot" + TOKEN + "/sendMessage"))
                .withRequestBody(matchingJsonPath("$.reply_to_message_id", equalTo("42"))));
    }

    @Test
    void sendMessage_returns_null_on_failure() {
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/sendMessage"))
                .willReturn(aResponse().withStatus(400).withBody("{\"ok\":false}")));

        TelegramMessage msg = client.sendMessage(TOKEN, "100", "Hello", null);

        assertThat(msg).isNull();
    }

    @Test
    void getChat_returns_chat() {
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/getChat"))
                .willReturn(okJson("""
                    {"ok":true,"result":{"id":100,"type":"supergroup","title":"Dev Team","description":"Engineering chat"}}
                    """)));

        TelegramChat chat = client.getChat(TOKEN, "100");

        assertThat(chat).isNotNull();
        assertThat(chat.title()).isEqualTo("Dev Team");
        assertThat(chat.type()).isEqualTo("supergroup");
    }

    @Test
    void getChatMemberCount_returns_count() {
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/getChatMemberCount"))
                .willReturn(okJson("{\"ok\":true,\"result\":42}")));

        int count = client.getChatMemberCount(TOKEN, "100");

        assertThat(count).isEqualTo(42);
    }

    @Test
    void getChatMember_returns_member() {
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/getChatMember"))
                .willReturn(okJson("""
                    {"ok":true,"result":{"user":{"id":55,"first_name":"Alice","is_bot":false},"status":"member"}}
                    """)));

        TelegramChatMember member = client.getChatMember(TOKEN, "100", 55);

        assertThat(member).isNotNull();
        assertThat(member.user().firstName()).isEqualTo("Alice");
        assertThat(member.status()).isEqualTo("member");
    }

    @Test
    void getChatAdministrators_returns_list() {
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/getChatAdministrators"))
                .willReturn(okJson("""
                    {"ok":true,"result":[{"user":{"id":1,"first_name":"Admin","is_bot":false},"status":"administrator"},{"user":{"id":2,"first_name":"Creator","is_bot":false},"status":"creator"}]}
                    """)));

        List<TelegramChatMember> admins = client.getChatAdministrators(TOKEN, "100");

        assertThat(admins).hasSize(2);
        assertThat(admins.get(0).status()).isEqualTo("administrator");
        assertThat(admins.get(1).status()).isEqualTo("creator");
    }

    @Test
    void setMessageReaction_returns_true() {
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/setMessageReaction"))
                .willReturn(okJson("{\"ok\":true,\"result\":true}")));

        boolean ok = client.setMessageReaction(TOKEN, "100", 42, "👍");

        assertThat(ok).isTrue();
    }

    @Test
    void setMyCommands_returns_true() {
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/setMyCommands"))
                .willReturn(okJson("{\"ok\":true,\"result\":true}")));

        boolean ok = client.setMyCommands(TOKEN,
                List.of(new TelegramBotClient.BotCommand("start", "Start the bot")));

        assertThat(ok).isTrue();
        wm.verify(postRequestedFor(urlPathEqualTo("/bot" + TOKEN + "/setMyCommands"))
                .withRequestBody(matchingJsonPath("$.commands[0].command", equalTo("start"))));
    }

    @Test
    void deleteMyCommands_returns_true() {
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/deleteMyCommands"))
                .willReturn(okJson("{\"ok\":true,\"result\":true}")));

        boolean ok = client.deleteMyCommands(TOKEN);

        assertThat(ok).isTrue();
    }
}
