package io.casehub.connectors.chat.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.casehub.connectors.chat.command.CommandDefinition;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.PresenceStatus;
import io.casehub.connectors.chat.model.SendResult;
import io.casehub.connectors.chat.spi.ChannelManagement;
import io.casehub.connectors.chat.spi.Commands;
import io.casehub.connectors.chat.spi.Discovery;
import io.casehub.connectors.chat.spi.MemberManagement;
import io.casehub.connectors.chat.spi.Members;
import io.casehub.connectors.chat.spi.MessageHistory;
import io.casehub.connectors.chat.spi.Messaging;
import io.casehub.connectors.chat.spi.Presence;
import io.casehub.connectors.chat.spi.Reactions;
import io.casehub.connectors.chat.spi.Threading;
import io.casehub.connectors.telegram.bot.TelegramBotClient;
import io.casehub.connectors.telegram.bot.model.TelegramChat;

class TelegramChatPlatformTest {

    private WireMockServer wm;
    private TelegramChatPlatform platform;
    private static final String TOKEN = "123456:ABC-DEF";

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wm.start();
        platform = new TelegramChatPlatform(
                new TelegramBotClient("http://localhost:" + wm.port()),
                Optional.of(TOKEN),
                Optional.empty());
        platform.init();
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    @Test
    void id_returns_telegram() {
        assertThat(platform.id()).isEqualTo("telegram");
    }

    @Test
    void supports_native_capabilities() {
        assertThat(platform.supports(Messaging.class)).isTrue();
        assertThat(platform.supports(Threading.class)).isTrue();
        assertThat(platform.supports(Discovery.class)).isTrue();
        assertThat(platform.supports(Reactions.class)).isTrue();
        assertThat(platform.supports(Members.class)).isTrue();
    }

    @Test
    void supports_presence_and_commands() {
        assertThat(platform.supports(Presence.class)).isTrue();
        assertThat(platform.supports(Commands.class)).isTrue();
    }

    @Test
    void does_not_support_degraded_capabilities() {
        assertThat(platform.supports(ChannelManagement.class)).isFalse();
        assertThat(platform.supports(MemberManagement.class)).isFalse();
        assertThat(platform.supports(MessageHistory.class)).isFalse();
    }

    @Test
    void send_message_returns_success() {
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/sendMessage"))
                .willReturn(okJson("""
                    {"ok":true,"result":{"message_id":42,"chat":{"id":100,"type":"private"},"from":{"id":1,"first_name":"Bot","is_bot":true},"text":"Hello","date":1700000000}}
                    """)));

        SendResult result = platform.messaging().send(
                new ChatChannelRef("100"), new ChatContent("Hello"));

        assertThat(result.ok()).isTrue();
        assertThat(result.messageRef().messageId()).isEqualTo("42");
    }

    @Test
    void threading_reply_passes_reply_to_message_id() {
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/sendMessage"))
                .willReturn(okJson("""
                    {"ok":true,"result":{"message_id":43,"chat":{"id":100,"type":"group"},"from":{"id":1,"first_name":"Bot","is_bot":true},"text":"Reply","date":1700000000}}
                    """)));

        SendResult result = platform.threading().reply(
                new ChatMessageRef(new ChatChannelRef("100"), "42"),
                new ChatContent("Reply"));

        assertThat(result.ok()).isTrue();
        wm.verify(postRequestedFor(urlPathEqualTo("/bot" + TOKEN + "/sendMessage"))
                .withRequestBody(matchingJsonPath("$.reply_to_message_id", equalTo("42"))));
    }

    @Test
    void discovery_returns_known_chats() {
        platform.registerKnownChat(new TelegramChat(100, "supergroup", "Dev Team", "Engineering", null, null, null));

        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/getChat"))
                .willReturn(okJson("""
                    {"ok":true,"result":{"id":100,"type":"supergroup","title":"Dev Team","description":"Engineering"}}
                    """)));
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/getChatMemberCount"))
                .willReturn(okJson("{\"ok\":true,\"result\":5}")));

        var channels = platform.discovery().listChannels();

        assertThat(channels).hasSize(1);
        assertThat(channels.get(0).name()).isEqualTo("Dev Team");
        assertThat(channels.get(0).memberCount()).isEqualTo(5);
    }

    @Test
    void discovery_returns_empty_without_known_chats() {
        assertThat(platform.discovery().listChannels()).isEmpty();
    }

    @Test
    void reactions_add_calls_set_message_reaction() {
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/setMessageReaction"))
                .willReturn(okJson("{\"ok\":true,\"result\":true}")));

        platform.reactions().add(
                new ChatMessageRef(new ChatChannelRef("100"), "42"), "👍");

        wm.verify(postRequestedFor(urlPathEqualTo("/bot" + TOKEN + "/setMessageReaction"))
                .withRequestBody(matchingJsonPath("$.message_id", equalTo("42")))
                .withRequestBody(matchingJsonPath("$.reaction[0].emoji", equalTo("👍"))));
    }

    @Test
    void reactions_list_returns_empty() {
        assertThat(platform.reactions().list(
                new ChatMessageRef(new ChatChannelRef("100"), "42")))
                .isEmpty();
    }

    @Test
    void members_returns_administrators() {
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/getChatAdministrators"))
                .willReturn(okJson("""
                    {"ok":true,"result":[{"user":{"id":1,"first_name":"Admin","is_bot":false},"status":"administrator"}]}
                    """)));

        var members = platform.members().list(new ChatChannelRef("100"));

        assertThat(members).hasSize(1);
        assertThat(members.get(0).displayName()).isEqualTo("Admin");
    }

    @Test
    void presence_maps_member_to_online() {
        platform.registerKnownChat(new TelegramChat(100, "group", "Test", null, null, null, null));
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/getChatMember"))
                .willReturn(okJson("""
                    {"ok":true,"result":{"user":{"id":42,"first_name":"Alice","is_bot":false},"status":"member"}}
                    """)));

        assertThat(platform.presence().of(new MemberRef("42"))).isEqualTo(PresenceStatus.ONLINE);
    }

    @Test
    void presence_maps_administrator_to_online() {
        platform.registerKnownChat(new TelegramChat(100, "group", "Test", null, null, null, null));
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/getChatMember"))
                .willReturn(okJson("""
                    {"ok":true,"result":{"user":{"id":42,"first_name":"Admin","is_bot":false},"status":"administrator"}}
                    """)));

        assertThat(platform.presence().of(new MemberRef("42"))).isEqualTo(PresenceStatus.ONLINE);
    }

    @Test
    void presence_maps_restricted_to_away() {
        platform.registerKnownChat(new TelegramChat(100, "group", "Test", null, null, null, null));
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/getChatMember"))
                .willReturn(okJson("""
                    {"ok":true,"result":{"user":{"id":42,"first_name":"Restricted","is_bot":false},"status":"restricted"}}
                    """)));

        assertThat(platform.presence().of(new MemberRef("42"))).isEqualTo(PresenceStatus.AWAY);
    }

    @Test
    void presence_maps_left_to_offline() {
        platform.registerKnownChat(new TelegramChat(100, "group", "Test", null, null, null, null));
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/getChatMember"))
                .willReturn(okJson("""
                    {"ok":true,"result":{"user":{"id":42,"first_name":"Gone","is_bot":false},"status":"left"}}
                    """)));

        assertThat(platform.presence().of(new MemberRef("42"))).isEqualTo(PresenceStatus.OFFLINE);
    }

    @Test
    void presence_maps_kicked_to_offline() {
        platform.registerKnownChat(new TelegramChat(100, "group", "Test", null, null, null, null));
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/getChatMember"))
                .willReturn(okJson("""
                    {"ok":true,"result":{"user":{"id":42,"first_name":"Banned","is_bot":false},"status":"kicked"}}
                    """)));

        assertThat(platform.presence().of(new MemberRef("42"))).isEqualTo(PresenceStatus.OFFLINE);
    }

    @Test
    void presence_returns_unknown_without_known_chats() {
        assertThat(platform.presence().of(new MemberRef("42"))).isEqualTo(PresenceStatus.UNKNOWN);
    }

    @Test
    void commands_register_calls_set_my_commands() {
        wm.stubFor(post(urlPathEqualTo("/bot" + TOKEN + "/setMyCommands"))
                .willReturn(okJson("{\"ok\":true,\"result\":true}")));

        platform.commands().registerAll(List.of(
                new CommandDefinition("help", "Show help", List.of()),
                new CommandDefinition("status", "Check status", List.of())));

        wm.verify(postRequestedFor(urlPathEqualTo("/bot" + TOKEN + "/setMyCommands"))
                .withRequestBody(matchingJsonPath("$.commands[0].command", equalTo("help")))
                .withRequestBody(matchingJsonPath("$.commands[0].description", equalTo("Show help")))
                .withRequestBody(matchingJsonPath("$.commands[1].command", equalTo("status"))));
    }

    @Test
    void degrades_when_unconfigured() {
        TelegramChatPlatform unconfigured = new TelegramChatPlatform(
                new TelegramBotClient("http://localhost:1"),
                Optional.empty(), Optional.empty());
        unconfigured.init();

        assertThat(unconfigured.supports(Messaging.class)).isFalse();
        assertThat(unconfigured.discovery().listChannels()).isEmpty();
    }
}
