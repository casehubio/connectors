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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamsChatPlatformTest {

    private ChatPlatform platform;

    @BeforeEach
    void setUp() {
        var graph = new MicrosoftGraphClient(request -> {
            String uri    = request.uri().toString();
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
                && !uri.contains("/replies")
                && !uri.contains("/setReaction")
                && !uri.contains("/unsetReaction")) {
                return new StubHttpResponse(201, messageResponseJson("msg-new"));
            }

            // Reply to message
            if (uri.contains("/replies") && method.equals("POST")) {
                return new StubHttpResponse(201, messageResponseJson("reply-1"));
            }

            // setReaction / unsetReaction
            if (uri.contains("/setReaction") || uri.contains("/unsetReaction")) {
                return new StubHttpResponse(204, "");
            }

            // Get single message (for reactions list)
            if (uri.matches(".*\\/messages\\/[^/]+$") && method.equals("GET")) {
                return new StubHttpResponse(200, messageWithReactionsJson());
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
    void supportsNineCapabilities() {
        assertThat(platform.supports(Messaging.class)).isTrue();
        assertThat(platform.supports(Threading.class)).isTrue();
        assertThat(platform.supports(Discovery.class)).isTrue();
        assertThat(platform.supports(Presence.class)).isTrue();
        assertThat(platform.supports(Reactions.class)).isTrue();
        assertThat(platform.supports(Members.class)).isTrue();
        assertThat(platform.supports(ChannelManagement.class)).isTrue();
        assertThat(platform.supports(MemberManagement.class)).isTrue();
        assertThat(platform.supports(MessageHistory.class)).isTrue();
    }

    @Test
    void commandsDegraded() {
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
        void createUsesConfiguredTeamId() {
            var requestUris = new java.util.ArrayList<String>();
            var graph = new MicrosoftGraphClient(request -> {
                requestUris.add(request.uri().toString());
                String uri = request.uri().toString();
                if (uri.contains("/teams/configured-team/channels") && request.method().equals("POST")) {
                    return new StubHttpResponse(201, channelJson());
                }
                return new StubHttpResponse(200, """
                                                 {"value":[]}""");
            });
            var configured = new TeamsChatPlatform(new TeamsClient(graph), null,
                                                   "configured-team").build();
            var channel = configured.channelManagement().create(
                    "New Channel", null, "desc", false);
            assertThat(channel.name()).isEqualTo("General");
            assertThat(requestUris).noneMatch(u -> u.contains("/me/joinedTeams"));
            assertThat(requestUris).anyMatch(u -> u.contains("/teams/configured-team/channels"));
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
    class AttachmentTest {

        @Test
        void sendMessageWithAttachmentUploadsAndReferences() {
            var requestedUris = new java.util.ArrayList<String>();
            var graph = new MicrosoftGraphClient(request -> {
                String uri    = request.uri().toString();
                String method = request.method();
                requestedUris.add(method + " " + uri);

                if (uri.contains("/filesFolder")) {
                    return new StubHttpResponse(200, """
                                                     {"id":"folder-1","parentReference":{"driveId":"drive-1"}}""");
                }
                if (uri.contains("/items/folder-1:/")) {
                    return new StubHttpResponse(201, """
                                                     {"id":"item-1","name":"0-test.pdf",\
                                                     "webUrl":"https://sharepoint.com/files/0-test.pdf"}""");
                }
                if (uri.contains("/messages") && method.equals("POST")) {
                    return new StubHttpResponse(201, messageResponseJson("msg-with-att"));
                }
                return new StubHttpResponse(200, "{\"value\":[]}");
            });
            var platformWithAtt = new TeamsChatPlatform(new TeamsClient(graph)).build();

            var att = new io.casehub.connectors.Attachment(
                    "test.pdf", "application/pdf", new byte[]{1, 2, 3});
            var content = new ChatContent("See attachment", null,
                                          java.util.List.of(att), java.util.List.of());
            var ref    = new ChatChannelRef("team-1:ch-1");
            var result = platformWithAtt.messaging().send(ref, content);

            assertThat(result.ok()).isTrue();
            assertThat(requestedUris).anyMatch(u -> u.contains("/filesFolder"));
            assertThat(requestedUris).anyMatch(u -> u.contains("/items/folder-1:/"));
            assertThat(requestedUris).anyMatch(u -> u.startsWith("POST") && u.contains("/messages"));
        }

        @Test
        void sendMessageUploadFailurePropagates() {
            var graph = new MicrosoftGraphClient(request -> {
                if (request.uri().toString().contains("/filesFolder")) {
                    return new StubHttpResponse(200, """
                                                     {"id":"folder-1","parentReference":{"driveId":"drive-1"}}""");
                }
                if (request.uri().toString().contains("/items/")) {
                    return new StubHttpResponse(403,
                                                "{\"error\":{\"code\":\"Forbidden\",\"message\":\"Access denied\"}}");
                }
                return new StubHttpResponse(200, "{\"value\":[]}");
            });
            var platformWithAtt = new TeamsChatPlatform(new TeamsClient(graph)).build();

            var att = new io.casehub.connectors.Attachment(
                    "secret.pdf", "application/pdf", new byte[]{1});
            var content = new ChatContent("text", null,
                                          java.util.List.of(att), java.util.List.of());
            var ref = new ChatChannelRef("team-1:ch-1");

            assertThatThrownBy(() -> platformWithAtt.messaging().send(ref, content))
                    .isInstanceOf(io.casehub.connectors.ProviderApiException.class);
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

    @Nested
    class ReactionsTest {
        @Test
        void addDoesNotThrow() {
            var channel = new ChatChannelRef("team-1:19:ch@thread.tacv2");
            var message = new ChatMessageRef(channel, "msg-1");
            assertThatCode(() -> platform.reactions().add(message, "like"))
                    .doesNotThrowAnyException();
        }

        @Test
        void removeDoesNotThrow() {
            var channel = new ChatChannelRef("team-1:19:ch@thread.tacv2");
            var message = new ChatMessageRef(channel, "msg-1");
            assertThatCode(() -> platform.reactions().remove(message, "like"))
                    .doesNotThrowAnyException();
        }

        @Test
        void listReturnsDistinctReactionTypes() {
            var channel   = new ChatChannelRef("team-1:19:ch@thread.tacv2");
            var message   = new ChatMessageRef(channel, "msg-1");
            var reactions = platform.reactions().list(message);
            assertThat(reactions).containsExactlyInAnyOrder("like", "heart");
        }
    }

    @Nested
    class PersonalChatTest {

        private ChatPlatform           personalPlatform;
        private java.util.List<String> capturedUrls;
        private java.util.List<String> capturedBodies;

        @BeforeEach
        void setUp() {
            capturedUrls   = new java.util.ArrayList<>();
            capturedBodies = new java.util.ArrayList<>();

            var graph = new MicrosoftGraphClient(request -> {
                capturedUrls.add(request.uri().toString());
                return new StubHttpResponse(200, "{\"value\":[]}");
            });

            var botClient = new BotFrameworkClient(null,
                                                   "*.botframework.com,smba.trafficmanager.net") {
                @Override
                String sendToConversation(String serviceUrl, String conversationId, String text) {
                    return "activity-" + conversationId;
                }

                @Override
                String replyToActivity(String serviceUrl, String conversationId,
                                       String activityId, String text) {
                    return "reply-" + activityId;
                }
            };

            personalPlatform = new TeamsChatPlatform(new TeamsClient(graph))
                                       .withBotClient(botClient, "https://smba.trafficmanager.net/teams/")
                                       .build();
        }

        @Test
        void sendToPersonalChannel_doesNotUseGraphApi() {
            var ref    = new ChatChannelRef("personal:conv-123");
            var result = personalPlatform.messaging().send(ref, new ChatContent("Hello DM"));
            assertThat(result.ok()).isTrue();
            assertThat(result.messageRef().channel().id()).isEqualTo("personal:conv-123");
            assertThat(capturedUrls).noneMatch(u -> u.contains("/teams/"));
        }

        @Test
        void replyToPersonalMessage_doesNotUseGraphApi() {
            var channelRef = new ChatChannelRef("personal:conv-123");
            var parentRef  = new ChatMessageRef(channelRef, "act-1");
            var result     = personalPlatform.threading().reply(parentRef, new ChatContent("Reply DM"));
            assertThat(result.ok()).isTrue();
            assertThat(capturedUrls).noneMatch(u -> u.contains("/teams/"));
        }

        @Test
        void sendToTeamChannel_stillUsesGraphApi() {
            var graph = new MicrosoftGraphClient(request -> {
                String uri = request.uri().toString();
                if (uri.contains("/messages") && request.method().equals("POST")) {
                    return new StubHttpResponse(201, messageResponseJson("msg-1"));
                }
                return new StubHttpResponse(200, "{\"value\":[]}");
            });
            var teamPlatform = new TeamsChatPlatform(new TeamsClient(graph)).build();
            var ref          = new ChatChannelRef("team-1:ch-1");
            var result       = teamPlatform.messaging().send(ref, new ChatContent("Team msg"));
            assertThat(result.ok()).isTrue();
        }

        @Test
        void listMembers_personalChannel_returnsEmpty() {
            var ref     = new ChatChannelRef("personal:conv-123");
            var members = personalPlatform.members().list(ref);
            assertThat(members).isEmpty();
            assertThat(capturedUrls).noneMatch(u -> u.contains("/members"));
        }

        @Test
        void messageHistory_personalChannel_returnsEmpty() {
            var ref      = new ChatChannelRef("personal:conv-123");
            var messages = personalPlatform.messageHistory().messages(ref, null);
            assertThat(messages).isEmpty();
            assertThat(capturedUrls).noneMatch(u -> u.contains("/messages"));
        }

        @Test
        void reactionsAdd_personalChannel_noOp() {
            var channelRef = new ChatChannelRef("personal:conv-123");
            var messageRef = new ChatMessageRef(channelRef, "msg-1");
            assertThatCode(() -> personalPlatform.reactions().add(messageRef, "like"))
                    .doesNotThrowAnyException();
            assertThat(capturedUrls).noneMatch(u -> u.contains("/setReaction"));
        }

        @Test
        void reactionsRemove_personalChannel_noOp() {
            var channelRef = new ChatChannelRef("personal:conv-123");
            var messageRef = new ChatMessageRef(channelRef, "msg-1");
            assertThatCode(() -> personalPlatform.reactions().remove(messageRef, "like"))
                    .doesNotThrowAnyException();
            assertThat(capturedUrls).noneMatch(u -> u.contains("/unsetReaction"));
        }

        @Test
        void reactionsList_personalChannel_returnsEmpty() {
            var channelRef = new ChatChannelRef("personal:conv-123");
            var messageRef = new ChatMessageRef(channelRef, "msg-1");
            var reactions  = personalPlatform.reactions().list(messageRef);
            assertThat(reactions).isEmpty();
            assertThat(capturedUrls).noneMatch(u -> u.contains("/messages/msg-1"));
        }

        @Test
        void memberManagementAdd_personalChannel_noOp() {
            var ref    = new ChatChannelRef("personal:conv-123");
            var member = new Member(new MemberRef("user-2"), "Bob");
            assertThatCode(() -> personalPlatform.memberManagement().add(ref, member))
                    .doesNotThrowAnyException();
            assertThat(capturedUrls).noneMatch(u -> u.contains("/members"));
        }

        @Test
        void memberManagementRemove_personalChannel_noOp() {
            var ref = new ChatChannelRef("personal:conv-123");
            assertThatCode(() -> personalPlatform.memberManagement().remove(ref, new MemberRef("user-2")))
                    .doesNotThrowAnyException();
            assertThat(capturedUrls).noneMatch(u -> u.contains("/members"));
        }

    }

    @Test
    void isPersonalChannel_detectsPrefix() {
        assertThat(TeamsChatPlatform.isPersonalChannel("personal:conv-1")).isTrue();
        assertThat(TeamsChatPlatform.isPersonalChannel("team-1:ch-1")).isFalse();
        assertThat(TeamsChatPlatform.isPersonalChannel(null)).isFalse();
    }

    @Test
    void extractConversationId_removesPrefix() {
        assertThat(TeamsChatPlatform.extractConversationId("personal:conv-123"))
                .isEqualTo("conv-123");
    }

    @Test
    void sendPersonalWithoutBotClient_throwsUnsupported() {
        var ref = new ChatChannelRef("personal:conv-1");
        assertThatThrownBy(() -> platform.messaging().send(ref, new ChatContent("test")))
                .isInstanceOf(UnsupportedOperationException.class);
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

    private static String messageWithReactionsJson() {
        return """
               {"id":"msg-1","createdDateTime":"2026-11-13T10:00:00Z",\
               "body":{"contentType":"text","content":"Hello"},\
               "reactions":[\
               {"reactionType":"like","user":{"user":{"id":"user-1"}}},\
               {"reactionType":"heart","user":{"user":{"id":"user-2"}}},\
               {"reactionType":"like","user":{"user":{"id":"user-3"}}}\
               ]}""";
    }


    @Nested
    class ProactiveMessagingTest {

        private final java.util.List<String>             botCalls = new java.util.ArrayList<>();
        private final InMemoryConversationReferenceStore store    = new InMemoryConversationReferenceStore();

        private ChatPlatform buildProactivePlatform() {
            var graph = new MicrosoftGraphClient(request ->
                                                         new StubHttpResponse(200, """
                                                                                   {"value":[]}"""));
            var botClient = new BotFrameworkClient(null,
                                                   "*.botframework.com,smba.trafficmanager.net") {
                @Override
                String sendToConversation(String serviceUrl, String conversationId, String text) {
                    botCalls.add("send:" + conversationId + ":" + text);
                    return "activity-" + conversationId;
                }

                @Override
                String replyToActivity(String serviceUrl, String conversationId,
                                       String activityId, String text) {
                    botCalls.add("reply:" + conversationId + ":" + activityId + ":" + text);
                    return "reply-" + activityId;
                }

                @Override
                String createConversation(String serviceUrl, String botAppId,
                                          String aadObjectId, String tenantId) {
                    botCalls.add("create:" + aadObjectId);
                    return "created-conv-for-" + aadObjectId;
                }
            };
            return new TeamsChatPlatform(new TeamsClient(graph))
                           .withBotClient(botClient, "https://smba.trafficmanager.net/teams/")
                           .withConversationReferenceStore(store)
                           .withProactiveConfig("tenant-1", "bot-app-id")
                           .build();
        }

        @Test
        void userChannel_storeMiss_createsConversationThenSends() {
            var p      = buildProactivePlatform();
            var ref    = new ChatChannelRef("user:aad-user-1");
            var result = p.messaging().send(ref, new ChatContent("Hello proactive"));
            assertThat(result.ok()).isTrue();
            assertThat(botCalls).containsExactly("create:aad-user-1",
                                                 "send:created-conv-for-aad-user-1:Hello proactive");
            assertThat(result.messageRef().channel().id()).startsWith("personal:");
        }

        @Test
        void userChannel_storeHit_sendsDirect() {
            store.store(new ConversationReference("aad-user-1", "existing-conv",
                                                  "https://smba.trafficmanager.net/teams/", "tenant-1"));
            var p      = buildProactivePlatform();
            var ref    = new ChatChannelRef("user:aad-user-1");
            var result = p.messaging().send(ref, new ChatContent("Hello again"));
            assertThat(result.ok()).isTrue();
            assertThat(botCalls).containsExactly("send:existing-conv:Hello again");
        }

        @Test
        void userChannel_storeMiss_storesRefAfterCreate() {
            var p = buildProactivePlatform();
            p.messaging().send(new ChatChannelRef("user:aad-user-1"),
                               new ChatContent("Hello"));
            assertThat(store.find("aad-user-1")).isPresent();
            assertThat(store.find("aad-user-1").get().conversationId())
                    .isEqualTo("created-conv-for-aad-user-1");
        }

        @Test
        void sendToConversation_returnsActivityId() {
            store.store(new ConversationReference("aad-user-1", "existing-conv",
                                                  "https://smba.trafficmanager.net/teams/", "tenant-1"));
            var p      = buildProactivePlatform();
            var ref    = new ChatChannelRef("user:aad-user-1");
            var result = p.messaging().send(ref, new ChatContent("Hello"));
            assertThat(result.messageRef().messageId()).isEqualTo("activity-existing-conv");
        }


        @Test
        void userChannel_noBotClient_throws() {
            var graph = new MicrosoftGraphClient(request ->
                                                         new StubHttpResponse(200, """
                                                                                   {"value":[]}"""));
            var p = new TeamsChatPlatform(new TeamsClient(graph)).build();
            assertThatThrownBy(() ->
                                       p.messaging().send(new ChatChannelRef("user:aad-user-1"),
                                                          new ChatContent("Hello")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void userChannel_reply_storeHit_repliesViaBot() {
            store.store(new ConversationReference("aad-user-1", "existing-conv",
                                                  "https://smba.trafficmanager.net/teams/", "tenant-1"));
            var p          = buildProactivePlatform();
            var channelRef = new ChatChannelRef("user:aad-user-1");
            var parentRef  = new ChatMessageRef(channelRef, "act-42");
            var result     = p.threading().reply(parentRef, new ChatContent("Reply to user"));
            assertThat(result.ok()).isTrue();
            assertThat(botCalls).containsExactly("reply:existing-conv:act-42:Reply to user");
            assertThat(result.messageRef().channel().id()).startsWith("personal:");
            assertThat(result.messageRef().messageId()).isEqualTo("reply-act-42");
        }

        @Test
        void userChannel_reply_storeMiss_createsConversationThenReplies() {
            var p          = buildProactivePlatform();
            var channelRef = new ChatChannelRef("user:aad-user-1");
            var parentRef  = new ChatMessageRef(channelRef, "act-99");
            var result     = p.threading().reply(parentRef, new ChatContent("Cold reply"));
            assertThat(result.ok()).isTrue();
            assertThat(botCalls).containsExactly("create:aad-user-1",
                                                 "reply:created-conv-for-aad-user-1:act-99:Cold reply");
            assertThat(result.messageRef().channel().id()).isEqualTo("personal:created-conv-for-aad-user-1");
            assertThat(store.find("aad-user-1")).isPresent();
        }


    }
}
