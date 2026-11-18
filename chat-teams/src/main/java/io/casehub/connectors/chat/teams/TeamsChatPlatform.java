package io.casehub.connectors.chat.teams;

import io.casehub.connectors.Attachment;
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
    private final String                    defaultTeamId;
    private       io.casehub.connectors.chat.spi.Commands commands;
    private       BotFrameworkClient                      botClient;
    private       String                                  defaultServiceUrl;
    private       ConversationReferenceStore              conversationReferenceStore;
    private       String                                  defaultTenantId;
    private       String                                  botAppId;


    public TeamsChatPlatform(TeamsClient client,
                             ServiceConnectionProvider connectionProvider) {
        this(client, connectionProvider, "");
    }

    public TeamsChatPlatform(TeamsClient client,
                             ServiceConnectionProvider connectionProvider, String defaultTeamId) {
        this.client             = client;
        this.connectionProvider = connectionProvider;
        this.defaultTeamId      = defaultTeamId == null ? "" : defaultTeamId;
    }


    TeamsChatPlatform(TeamsClient client) {
        this(client, null, "");
    }


    public TeamsChatPlatform withCommands(io.casehub.connectors.chat.spi.Commands commands) {
        this.commands = commands;
        return this;
    }

    public TeamsChatPlatform withBotClient(BotFrameworkClient botClient, String defaultServiceUrl) {
        this.botClient         = botClient;
        this.defaultServiceUrl = defaultServiceUrl;
        return this;
    }

    public TeamsChatPlatform withConversationReferenceStore(ConversationReferenceStore store) {
        this.conversationReferenceStore = store;
        return this;
    }

    public TeamsChatPlatform withProactiveConfig(String defaultTenantId, String botAppId) {
        this.defaultTenantId = defaultTenantId;
        this.botAppId        = botAppId;
        return this;
    }


    public ChatPlatform build() {
        var builder = ChatPlatform.builder(PLATFORM_ID)
                                  .messaging(this::sendMessage)
                                  .threading(this::replyToMessage)
                                  .discovery(this::listChannels)
                                  .presence(new TeamsPresence())
                                  .reactions(new TeamsReactions())
                                  .members(this::listMembers)
                                  .channelManagement(new TeamsChannelManagement())
                                  .memberManagement(new TeamsMemberManagement())
                                  .messageHistory(this::listMessageHistory);
        if (commands != null) {
            builder.commands(commands);
        }
        return builder.build();
    }

    private SendResult sendMessage(ChatChannelRef channel, ChatContent content) {
        if (isPersonalChannel(channel.id())) {
            return sendPersonalMessage(extractConversationId(channel.id()), content);
        }
        if (isUserChannel(channel.id())) {
            return sendProactiveMessage(extractAadObjectId(channel.id()), content);
        }
        var tc = TeamsMapper.parseTeamAndChannel(channel.id());
        return TokenRetry.withTokenRetry(this::resolveApplicationToken,
                                         token -> {
                                             var uploadedRefs = uploadAttachments(tc[0], tc[1],
                                                                                  content.attachments(), token);
                                             var result = client.sendMessage(tc[0], tc[1],
                                                                             TeamsMapper.toMessageBody(content, uploadedRefs), token);
                                             return TeamsMapper.toSendResult(tc[0], tc[1], result);
                                         });
    }

    private SendResult replyToMessage(ChatMessageRef parent, ChatContent content) {
        if (isPersonalChannel(parent.channel().id())) {
            if (botClient == null || defaultServiceUrl == null || defaultServiceUrl.isBlank()) {
                throw new UnsupportedOperationException(
                        "Personal chat reply requires BotFrameworkClient and default service URL");
            }
            String conversationId = extractConversationId(parent.channel().id());
            String activityId = botClient.replyToActivity(defaultServiceUrl, conversationId,
                                      parent.messageId(), content.text());
            return new SendResult(true,
                                  new ChatMessageRef(parent.channel(), activityId),
                                  java.time.Instant.now(), null);
        }
        if (isUserChannel(parent.channel().id())) {
            return replyToUserChannel(extractAadObjectId(parent.channel().id()), parent, content);
        }
        var tc = TeamsMapper.parseTeamAndChannel(parent.channel().id());
        return TokenRetry.withTokenRetry(this::resolveApplicationToken,
                                         token -> {
                                             var uploadedRefs = uploadAttachments(tc[0], tc[1],
                                                                                  content.attachments(), token);
                                             var result = client.replyToMessage(tc[0], tc[1],
                                                                                parent.messageId(), TeamsMapper.toMessageBody(content, uploadedRefs), token);
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
        if (isPersonalChannel(channel.id())) {return List.of();}
        if (isUserChannel(channel.id())) {return List.of();}
        var tc = TeamsMapper.parseTeamAndChannel(channel.id());
        return TokenRetry.withTokenRetry(this::resolveDelegatedToken,
                                         token -> client.listMembers(tc[0], token).stream()
                                                        .map(TeamsMapper::toMember).toList());
    }

    private List<ReceivedMessage> listMessageHistory(ChatChannelRef channel, Instant since) {
        if (isPersonalChannel(channel.id())) {return List.of();}
        if (isUserChannel(channel.id())) {return List.of();}
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


    private static String safeAttachmentName(int index, Attachment attachment) {
        String name = attachment.filename();
        if (name == null || name.isBlank()) {
            name = "attachment-" + index + extensionFromMimeType(attachment.contentType());
        }
        return index + "-" + name;
    }

    private static String extensionFromMimeType(String contentType) {
        if (contentType == null) {return ".bin";}
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "text/plain" -> ".txt";
            case "application/msword" -> ".doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            default -> ".bin";
        };
    }

    private List<UploadedRef> uploadAttachments(String teamId, String channelId,
            List<Attachment> attachments, String token) {
        if (attachments.isEmpty()) {
            return List.of();
        }
        var    folder   = client.getChannelFilesFolder(teamId, channelId, token);
        String folderId = folder.getString("id");
        String driveId  = folder.getJsonObject("parentReference").getString("driveId");
        var    refs     = new ArrayList<UploadedRef>();
        for (int i = 0; i < attachments.size(); i++) {
            var    att  = attachments.get(i);
            String name = safeAttachmentName(i, att);
            var    item = client.uploadToChannelFolder(
                    driveId, folderId, name, att.contentType(), att.content(), token);
            refs.add(new UploadedRef(name, item.getString("webUrl")));
        }
        return List.copyOf(refs);
    }

    static boolean isPersonalChannel(String channelId) {
        return channelId != null && channelId.startsWith("personal:");
    }

    static String extractConversationId(String channelId) {
        return channelId.substring("personal:".length());
    }

    static boolean isUserChannel(String channelId) {
        return channelId != null && channelId.startsWith("user:");
    }

    static String extractAadObjectId(String channelId) {
        return channelId.substring("user:".length());
    }


    private SendResult sendPersonalMessage(String conversationId, ChatContent content) {
        if (botClient == null) {
            throw new UnsupportedOperationException(
                    "Personal chat messaging requires BotFrameworkClient — configure teams-bot properties");
        }
        String serviceUrl = defaultServiceUrl;
        if (serviceUrl == null || serviceUrl.isBlank()) {
            throw new UnsupportedOperationException(
                    "Personal chat messaging requires a default service URL — set casehub.connectors.teams-bot.default-service-url");
        }
        String activityId = botClient.sendToConversation(serviceUrl, conversationId, content.text());
        return new SendResult(true,
                              new ChatMessageRef(new ChatChannelRef("personal:" + conversationId), activityId),
                              java.time.Instant.now(), null);
    }

    private SendResult sendProactiveMessage(String aadObjectId, ChatContent content) {
        if (botClient == null) {
            throw new UnsupportedOperationException(
                    "Proactive messaging requires BotFrameworkClient — configure teams-bot properties");
        }
        String serviceUrl = defaultServiceUrl;
        if (serviceUrl == null || serviceUrl.isBlank()) {
            throw new UnsupportedOperationException(
                    "Proactive messaging requires a default service URL — set casehub.connectors.teams-bot.default-service-url");
        }

        if (conversationReferenceStore != null) {
            var existing = conversationReferenceStore.find(aadObjectId);
            if (existing.isPresent()) {
                String resolvedServiceUrl = existing.get().serviceUrl();
                String cachedConversationId = existing.get().conversationId();
                String activityId = botClient.sendToConversation(resolvedServiceUrl, cachedConversationId, content.text());
                return new SendResult(true,
                                      new ChatMessageRef(new ChatChannelRef("personal:" + cachedConversationId), activityId),
                                      java.time.Instant.now(), null);
            }
        }

        if (defaultTenantId == null || defaultTenantId.isBlank()
            || botAppId == null || botAppId.isBlank()) {
            throw new UnsupportedOperationException(
                    "Proactive messaging cold-start requires default-tenant-id and app-id — "
                    + "configure casehub.connectors.teams-bot properties");
        }

        String conversationId = botClient.createConversation(serviceUrl, botAppId, aadObjectId, defaultTenantId);

        if (conversationReferenceStore != null) {
            conversationReferenceStore.store(
                    new ConversationReference(aadObjectId, conversationId, serviceUrl, defaultTenantId));
        }

        String activityId = botClient.sendToConversation(serviceUrl, conversationId, content.text());
        return new SendResult(true,
                              new ChatMessageRef(new ChatChannelRef("personal:" + conversationId), activityId),
                              java.time.Instant.now(), null);
    }

    private SendResult replyToUserChannel(String aadObjectId, ChatMessageRef parent, ChatContent content) {
        if (botClient == null || defaultServiceUrl == null || defaultServiceUrl.isBlank()) {
            throw new UnsupportedOperationException(
                    "User channel reply requires BotFrameworkClient and default service URL");
        }
        String serviceUrl     = defaultServiceUrl;
        String conversationId = null;

        if (conversationReferenceStore != null) {
            var existing = conversationReferenceStore.find(aadObjectId);
            if (existing.isPresent()) {
                serviceUrl     = existing.get().serviceUrl();
                conversationId = existing.get().conversationId();
            }
        }
        if (conversationId == null) {
            if (defaultTenantId == null || defaultTenantId.isBlank()
                || botAppId == null || botAppId.isBlank()) {
                throw new UnsupportedOperationException(
                        "User channel reply cold-start requires default-tenant-id and app-id — "
                        + "configure casehub.connectors.teams-bot properties");
            }
            conversationId = botClient.createConversation(serviceUrl, botAppId, aadObjectId, defaultTenantId);
            if (conversationReferenceStore != null) {
                conversationReferenceStore.store(
                        new ConversationReference(aadObjectId, conversationId, serviceUrl, defaultTenantId));
            }
        }

        String activityId = botClient.replyToActivity(serviceUrl, conversationId,
                                                      parent.messageId(), content.text());
        return new SendResult(true,
                              new ChatMessageRef(new ChatChannelRef("personal:" + conversationId), activityId),
                              java.time.Instant.now(), null);
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
                        String teamId;
                        if (!defaultTeamId.isEmpty()) {
                            teamId = defaultTeamId;
                        } else {
                            var teams = client.listJoinedTeams(token);
                            if (teams.isEmpty()) {
                                throw new IllegalStateException("No teams available");
                            }
                            teamId = teams.get(0).getString("id");
                        }
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
            if (isPersonalChannel(channelId)) return;
            if (isUserChannel(channelId)) return;
            var tc = TeamsMapper.parseTeamAndChannel(channelId);
            TokenRetry.withTokenRetryVoid(
                    TeamsChatPlatform.this::resolveDelegatedToken,
                    token -> client.deleteChannel(tc[0], tc[1], token));
        }

        @Override
        public Optional<Channel> find(String channelId) {
            if (isPersonalChannel(channelId)) return Optional.empty();
            if (isUserChannel(channelId)) return Optional.empty();
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
            if (isPersonalChannel(channel.id())) {return;}
            if (isUserChannel(channel.id())) {return;}
            var tc = TeamsMapper.parseTeamAndChannel(channel.id());
            TokenRetry.withTokenRetryVoid(
                    TeamsChatPlatform.this::resolveDelegatedToken,
                    token -> client.addMember(tc[0], member.ref().id(), token));
        }

        @Override
        public void remove(ChatChannelRef channel, MemberRef member) {
            if (isPersonalChannel(channel.id())) {return;}
            if (isUserChannel(channel.id())) {return;}
            var tc = TeamsMapper.parseTeamAndChannel(channel.id());
            TokenRetry.withTokenRetryVoid(
                    TeamsChatPlatform.this::resolveDelegatedToken,
                    token -> client.removeMember(tc[0], member.id(), token));
        }
    }

    private class TeamsReactions implements io.casehub.connectors.chat.spi.Reactions {
        @Override
        public void add(ChatMessageRef message, String emoji) {
            if (isPersonalChannel(message.channel().id())) {return;}
            if (isUserChannel(message.channel().id())) {return;}
            var tc = TeamsMapper.parseTeamAndChannel(message.channel().id());
            TokenRetry.withTokenRetryVoid(TeamsChatPlatform.this::resolveApplicationToken,
                                          token -> client.setReaction(tc[0], tc[1], message.messageId(), emoji, token));
        }

        @Override
        public void remove(ChatMessageRef message, String emoji) {
            if (isPersonalChannel(message.channel().id())) {return;}
            if (isUserChannel(message.channel().id())) {return;}
            var tc = TeamsMapper.parseTeamAndChannel(message.channel().id());
            TokenRetry.withTokenRetryVoid(TeamsChatPlatform.this::resolveApplicationToken,
                                          token -> client.unsetReaction(tc[0], tc[1], message.messageId(), emoji, token));
        }

        @Override
        public List<String> list(ChatMessageRef message) {
            if (isPersonalChannel(message.channel().id())) {return List.of();}
            if (isUserChannel(message.channel().id())) {return List.of();}
            var tc = TeamsMapper.parseTeamAndChannel(message.channel().id());
            return TokenRetry.withTokenRetry(TeamsChatPlatform.this::resolveApplicationToken,
                                             token -> {
                                                 var msg = client.getMessage(tc[0], tc[1], message.messageId(), token);
                                                 if (!msg.containsKey("reactions")) {
                                                     return List.of();
                                                 }
                                                 return msg.getJsonArray("reactions").stream()
                                                           .map(r -> r.asJsonObject().getString("reactionType"))
                                                           .distinct()
                                                           .toList();
                                             });
        }
    }

}
