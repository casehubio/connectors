package io.casehub.connectors.chat.teams;

import io.casehub.connectors.chat.model.Channel;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.Member;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.PresenceStatus;
import io.casehub.connectors.chat.model.ReceivedMessage;
import io.casehub.connectors.chat.model.SendResult;
import io.casehub.connectors.chat.spi.ChannelManagement;
import io.casehub.connectors.chat.spi.ChatPlatform;
import io.casehub.connectors.chat.spi.MemberManagement;
import io.casehub.connectors.chat.spi.Presence;
import io.casehub.connectors.microsoft.TokenRetry;
import io.casehub.platform.api.authn.ServiceConnectionProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TeamsChatPlatform {

    static final String PLATFORM_ID = "microsoft-teams";
    static final String APPLICATION_ACTOR = "__application__";

    private final TeamsClient client;
    private final ServiceConnectionProvider connectionProvider;

    public TeamsChatPlatform(TeamsClient client,
            ServiceConnectionProvider connectionProvider) {
        this.client = client;
        this.connectionProvider = connectionProvider;
    }

    TeamsChatPlatform(TeamsClient client) {
        this(client, null);
    }

    public ChatPlatform build() {
        return ChatPlatform.builder(PLATFORM_ID)
                .messaging(this::sendMessage)
                .threading(this::replyToMessage)
                .discovery(this::listChannels)
                .presence(new TeamsPresence())
                .members(this::listMembers)
                .channelManagement(new TeamsChannelManagement())
                .memberManagement(new TeamsMemberManagement())
                .messageHistory(this::listMessageHistory)
                .build();
    }

    private SendResult sendMessage(ChatChannelRef channel, ChatContent content) {
        var tc = TeamsMapper.parseTeamAndChannel(channel.id());
        return TokenRetry.withTokenRetry(this::resolveApplicationToken,
                token -> {
                    var result = client.sendMessage(tc[0], tc[1],
                            TeamsMapper.toMessageBody(content), token);
                    return TeamsMapper.toSendResult(tc[0], tc[1], result);
                });
    }

    private SendResult replyToMessage(ChatMessageRef parent, ChatContent content) {
        var tc = TeamsMapper.parseTeamAndChannel(parent.channel().id());
        return TokenRetry.withTokenRetry(this::resolveApplicationToken,
                token -> {
                    var result = client.replyToMessage(tc[0], tc[1],
                            parent.messageId(), TeamsMapper.toMessageBody(content), token);
                    return TeamsMapper.toSendResult(tc[0], tc[1], result);
                });
    }

    private List<Channel> listChannels() {
        return TokenRetry.withTokenRetry(this::resolveDelegatedToken,
                token -> {
                    var teams = client.listJoinedTeams(token);
                    List<Channel> channels = new ArrayList<>();
                    for (var team : teams) {
                        String teamId = team.getString("id");
                        var teamChannels = client.listChannels(teamId, token);
                        for (var ch : teamChannels) {
                            channels.add(TeamsMapper.toChannel(teamId, ch));
                        }
                    }
                    return List.copyOf(channels);
                });
    }

    private List<Member> listMembers(ChatChannelRef channel) {
        var tc = TeamsMapper.parseTeamAndChannel(channel.id());
        return TokenRetry.withTokenRetry(this::resolveDelegatedToken,
                token -> client.listMembers(tc[0], token).stream()
                        .map(TeamsMapper::toMember).toList());
    }

    private List<ReceivedMessage> listMessageHistory(ChatChannelRef channel, Instant since) {
        var tc = TeamsMapper.parseTeamAndChannel(channel.id());
        return TokenRetry.withTokenRetry(this::resolveApplicationToken,
                token -> client.listMessages(tc[0], tc[1], token).stream()
                        .filter(msg -> {
                            Instant created = Instant.parse(
                                    msg.getString("createdDateTime"));
                            return since == null || created.isAfter(since);
                        })
                        .map(msg -> TeamsMapper.toReceivedMessage(
                                PLATFORM_ID, tc[0], tc[1], msg))
                        .toList());
    }

    private String resolveDelegatedToken() {
        if (connectionProvider == null) return "test-token";
        return connectionProvider.getAccessToken(null, "microsoft", "default")
                .accessToken();
    }

    private String resolveApplicationToken() {
        if (connectionProvider == null) return "test-app-token";
        return connectionProvider.getAccessToken(APPLICATION_ACTOR, "microsoft-app",
                "default").accessToken();
    }

    private class TeamsPresence implements Presence {
        @Override
        public PresenceStatus of(MemberRef member) {
            return TokenRetry.withTokenRetry(
                    TeamsChatPlatform.this::resolveDelegatedToken,
                    token -> TeamsMapper.toPresenceStatus(
                            client.getPresence(member.id(), token)));
        }

        @Override
        public void set(MemberRef member, PresenceStatus status) {
            // Teams Graph API does not support setting presence for other users
        }
    }

    private class TeamsChannelManagement implements ChannelManagement {
        @Override
        public Channel create(String name, String topic, String description,
                boolean isPrivate) {
            return TokenRetry.withTokenRetry(
                    TeamsChatPlatform.this::resolveDelegatedToken,
                    token -> {
                        var teams = client.listJoinedTeams(token);
                        if (teams.isEmpty()) {
                            throw new IllegalStateException("No teams available");
                        }
                        String teamId = teams.get(0).getString("id");
                        var body = jakarta.json.Json.createObjectBuilder()
                                .add("displayName", name)
                                .add("membershipType",
                                        isPrivate ? "private" : "standard");
                        if (description != null) {
                            body.add("description", description);
                        }
                        var result = client.createChannel(teamId, body.build(), token);
                        return TeamsMapper.toChannel(teamId, result);
                    });
        }

        @Override
        public void delete(String channelId) {
            var tc = TeamsMapper.parseTeamAndChannel(channelId);
            TokenRetry.withTokenRetryVoid(
                    TeamsChatPlatform.this::resolveDelegatedToken,
                    token -> client.deleteChannel(tc[0], tc[1], token));
        }

        @Override
        public Optional<Channel> find(String channelId) {
            var tc = TeamsMapper.parseTeamAndChannel(channelId);
            return TokenRetry.withTokenRetry(
                    TeamsChatPlatform.this::resolveDelegatedToken,
                    token -> {
                        try {
                            var json = client.getChannel(tc[0], tc[1], token);
                            return Optional.of(
                                    TeamsMapper.toChannel(tc[0], json));
                        } catch (Exception e) {
                            return Optional.empty();
                        }
                    });
        }
    }

    private class TeamsMemberManagement implements MemberManagement {
        @Override
        public void add(ChatChannelRef channel, Member member) {
            var tc = TeamsMapper.parseTeamAndChannel(channel.id());
            TokenRetry.withTokenRetryVoid(
                    TeamsChatPlatform.this::resolveDelegatedToken,
                    token -> client.addMember(tc[0], member.ref().id(), token));
        }

        @Override
        public void remove(ChatChannelRef channel, MemberRef member) {
            var tc = TeamsMapper.parseTeamAndChannel(channel.id());
            TokenRetry.withTokenRetryVoid(
                    TeamsChatPlatform.this::resolveDelegatedToken,
                    token -> client.removeMember(tc[0], member.id(), token));
        }
    }
}
