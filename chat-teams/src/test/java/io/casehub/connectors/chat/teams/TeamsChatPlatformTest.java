package io.casehub.connectors.chat.teams;

import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.Member;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.PresenceStatus;
import io.casehub.connectors.chat.spi.ChannelManagement;
import io.casehub.connectors.chat.spi.ChatPlatform;
import io.casehub.connectors.chat.spi.Commands;
import io.casehub.connectors.chat.spi.Discovery;
import io.casehub.connectors.chat.spi.MemberManagement;
import io.casehub.connectors.chat.spi.Members;
import io.casehub.connectors.chat.spi.MessageHistory;
import io.casehub.connectors.chat.spi.Messaging;
import io.casehub.connectors.chat.spi.Presence;
import io.casehub.connectors.chat.spi.Reactions;
import io.casehub.connectors.chat.spi.Threading;
import io.casehub.connectors.microsoft.MicrosoftGraphClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class TeamsChatPlatformTest {

    private ChatPlatform platform;

    @BeforeEach
    void setUp() {
        var graph = new MicrosoftGraphClient(request -> {
            String uri = request.uri().toString();
            String method = request.method();

            // List joined teams
            if (uri.contains("/me/joinedTeams")) {
                return new StubHttpResponse(200, """
                        {"value":[{"id":"team-1","displayName":"Engineering"}]}""");
            }

            // List channels for a team
            if (uri.matches(".*\\/teams\\/[^/]+\\/channels$") && method.equals("GET")) {
                return new StubHttpResponse(200, """
                        {"value":[%s]}""".formatted(channelJson()));
            }

            // Send message
            if (uri.contains("/messages") && method.equals("POST")
                    && !uri.contains("/replies")) {
                return new StubHttpResponse(201, messageResponseJson("msg-new"));
            }

            // Reply to message
            if (uri.contains("/replies") && method.equals("POST")) {
                return new StubHttpResponse(201, messageResponseJson("reply-1"));
            }

            // Get presence
            if (uri.contains("/presence")) {
                return new StubHttpResponse(200, """
                        {"availability":"Available","activity":"Available"}""");
            }

            // List members
            if (uri.contains("/members") && method.equals("GET")) {
                return new StubHttpResponse(200, """
                        {"value":[{"id":"membership-1","userId":"user-1",\
                        "displayName":"Alice","roles":["owner"]}]}""");
            }

            // Create channel
            if (uri.matches(".*\\/teams\\/[^/]+\\/channels$") && method.equals("POST")) {
                return new StubHttpResponse(201, channelJson());
            }

            // Delete channel
            if (uri.matches(".*\\/channels\\/[^/]+$") && method.equals("DELETE")) {
                return new StubHttpResponse(204, "");
            }

            // Get channel
            if (uri.matches(".*\\/channels\\/[^/]+$") && method.equals("GET")) {
                return new StubHttpResponse(200, channelJson());
            }

            // List messages (history)
            if (uri.contains("/messages") && method.equals("GET")) {
                return new StubHttpResponse(200, """
                        {"value":[%s]}""".formatted(historyMessageJson()));
            }

            // Add/remove member (POST/DELETE on members)
            if (uri.contains("/members") && method.equals("POST")) {
                return new StubHttpResponse(201, """
                        {"id":"membership-new"}""");
            }
            if (uri.contains("/members") && method.equals("DELETE")) {
                return new StubHttpResponse(204, "");
            }

            return new StubHttpResponse(200, """
                    {"value":[]}""");
        });

        platform = new TeamsChatPlatform(new TeamsClient(graph)).build();
    }

    @Test
    void idReturnsMicrosoftTeams() {
        assertThat(platform.id()).isEqualTo("microsoft-teams");
    }

    @Test
    void supportsEightCapabilities() {
        assertThat(platform.supports(Messaging.class)).isTrue();
        assertThat(platform.supports(Threading.class)).isTrue();
        assertThat(platform.supports(Discovery.class)).isTrue();
        assertThat(platform.supports(Presence.class)).isTrue();
        assertThat(platform.supports(Members.class)).isTrue();
        assertThat(platform.supports(ChannelManagement.class)).isTrue();
        assertThat(platform.supports(MemberManagement.class)).isTrue();
        assertThat(platform.supports(MessageHistory.class)).isTrue();
    }

    @Test
    void reactionsAndCommandsDegraded() {
        assertThat(platform.supports(Reactions.class)).isFalse();
        assertThat(platform.supports(Commands.class)).isFalse();
    }

    @Nested
    class MessagingTest {
        @Test
        void sendReturnsSuccess() {
            var ref = new ChatChannelRef("team-1:19:ch@thread.tacv2");
            var result = platform.messaging().send(ref, new ChatContent("Hello"));
            assertThat(result.ok()).isTrue();
            assertThat(result.messageRef().messageId()).isEqualTo("msg-new");
        }
    }

    @Nested
    class ThreadingTest {
        @Test
        void replyReturnsSuccess() {
            var channelRef = new ChatChannelRef("team-1:19:ch@thread.tacv2");
            var parentRef = new ChatMessageRef(channelRef, "msg-1");
            var result = platform.threading().reply(parentRef, new ChatContent("Reply"));
            assertThat(result.ok()).isTrue();
            assertThat(result.messageRef().messageId()).isEqualTo("reply-1");
        }
    }

    @Nested
    class DiscoveryTest {
        @Test
        void listChannelsReturnsAll() {
            var channels = platform.discovery().listChannels();
            assertThat(channels).hasSize(1);
            assertThat(channels.get(0).name()).isEqualTo("General");
            assertThat(channels.get(0).ref().id()).startsWith("team-1:");
        }
    }

    @Nested
    class PresenceTest {
        @Test
        void ofReturnsStatus() {
            var status = platform.presence().of(new MemberRef("user-1"));
            assertThat(status).isEqualTo(PresenceStatus.ONLINE);
        }
    }

    @Nested
    class MembersTest {
        @Test
        void listReturnsMembers() {
            var ref = new ChatChannelRef("team-1:19:ch@thread.tacv2");
            var members = platform.members().list(ref);
            assertThat(members).hasSize(1);
            assertThat(members.get(0).displayName()).isEqualTo("Alice");
            assertThat(members.get(0).ref().id()).isEqualTo("user-1");
        }
    }

    @Nested
    class ChannelManagementTest {
        @Test
        void createReturnsChannel() {
            var channel = platform.channelManagement().create(
                    "New Channel", null, "A new channel", false);
            assertThat(channel.name()).isEqualTo("General");
        }

        @Test
        void deleteDoesNotThrow() {
            assertThatCode(() -> platform.channelManagement()
                    .delete("team-1:19:ch@thread.tacv2"))
                    .doesNotThrowAnyException();
        }

        @Test
        void findReturnsChannel() {
            var channel = platform.channelManagement()
                    .find("team-1:19:ch@thread.tacv2");
            assertThat(channel).isPresent();
            assertThat(channel.get().name()).isEqualTo("General");
        }
    }

    @Nested
    class MemberManagementTest {
        @Test
        void addDoesNotThrow() {
            var ref = new ChatChannelRef("team-1:19:ch@thread.tacv2");
            var member = new Member(new MemberRef("user-2"), "Bob");
            assertThatCode(() -> platform.memberManagement().add(ref, member))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class MessageHistoryTest {
        @Test
        void messagesReturnsHistory() {
            var ref = new ChatChannelRef("team-1:19:ch@thread.tacv2");
            var messages = platform.messageHistory().messages(ref, null);
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0).content().text()).isEqualTo("Hello world");
            assertThat(messages.get(0).sender().id()).isEqualTo("user-1");
        }

        @Test
        void messagesFiltersBySince() {
            var ref = new ChatChannelRef("team-1:19:ch@thread.tacv2");
            var messages = platform.messageHistory().messages(ref,
                    Instant.parse("2030-01-01T00:00:00Z"));
            assertThat(messages).isEmpty();
        }
    }

    private static String channelJson() {
        return """
                {"id":"19:general@thread.tacv2","displayName":"General",\
                "description":"Main channel","membershipType":"standard"}""";
    }

    private static String messageResponseJson(String id) {
        return """
                {"id":"%s","createdDateTime":"2026-11-13T10:00:00Z",\
                "body":{"contentType":"text","content":"test"}}"""
                .formatted(id);
    }

    private static String historyMessageJson() {
        return """
                {"id":"msg-hist-1","createdDateTime":"2026-11-13T09:00:00Z",\
                "body":{"contentType":"text","content":"Hello world"},\
                "from":{"user":{"id":"user-1","displayName":"Alice"}}}""";
    }
}
