package io.casehub.connectors.chat.telegram;

import io.casehub.connectors.chat.degraded.ChannelFallbackThreading;
import io.casehub.connectors.chat.degraded.EmptyDiscovery;
import io.casehub.connectors.chat.degraded.EmptyMembers;
import io.casehub.connectors.chat.degraded.EmptyMessageHistory;
import io.casehub.connectors.chat.degraded.NoOpChannelManagement;
import io.casehub.connectors.chat.degraded.NoOpCommands;
import io.casehub.connectors.chat.degraded.NoOpMemberManagement;
import io.casehub.connectors.chat.degraded.NoOpReactions;
import io.casehub.connectors.chat.degraded.UnknownPresence;
import io.casehub.connectors.chat.model.Channel;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.Member;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.SendResult;
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
import io.casehub.connectors.telegram.bot.TelegramBotClient;
import io.casehub.connectors.telegram.bot.model.TelegramChat;
import io.casehub.connectors.telegram.bot.model.TelegramMessage;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ApplicationScoped
public class TelegramChatPlatform implements ChatPlatform {

    private static final Logger LOG = Logger.getLogger(TelegramChatPlatform.class.getName());

    private static final Set<Class<?>> NATIVE_CAPABILITIES = Set.of(
            Messaging.class, Threading.class, Discovery.class,
            Reactions.class, Members.class);

    private final TelegramBotClient client;
    private final String token;

    private final ConcurrentHashMap<String, TelegramChat> knownChats = new ConcurrentHashMap<>();

    private Set<Class<?>> activeCapabilities = Set.of();
    private Messaging messaging;
    private Threading threading;
    private Discovery discovery;
    private Reactions reactions;
    private Presence presence;
    private Members members;
    private Commands commands;
    private ChannelManagement channelManagement;
    private MemberManagement memberManagement;
    private MessageHistory messageHistory;

    @Inject
    public TelegramChatPlatform(
            final TelegramBotClient client,
            @ConfigProperty(name = "casehub.telegram.bot.token") final Optional<String> token,
            @ConfigProperty(name = "casehub.telegram.webhook.secret-token") final Optional<String> webhookSecret) {
        this.client = client;
        this.token = token.orElse("");
    }

    @PostConstruct
    void init() {
        if (token.isBlank()) {
            LOG.warning("telegram: not configured, platform inactive");
            initDegraded();
            return;
        }

        activeCapabilities = NATIVE_CAPABILITIES;
        messaging = this::sendMessage;
        threading = this::replyToMessage;
        discovery = this::listChannels;
        reactions = new TelegramReactions();
        presence = new UnknownPresence();
        members = this::listMembers;
        commands = new NoOpCommands();
        channelManagement = new NoOpChannelManagement();
        memberManagement = new NoOpMemberManagement();
        messageHistory = new EmptyMessageHistory();
    }

    private void initDegraded() {
        activeCapabilities = Set.of();
        messaging = (channel, content) -> SendResult.failure("Telegram not configured");
        threading = new ChannelFallbackThreading(messaging);
        discovery = new EmptyDiscovery();
        reactions = new NoOpReactions();
        presence = new UnknownPresence();
        members = new EmptyMembers();
        commands = new NoOpCommands();
        channelManagement = new NoOpChannelManagement();
        memberManagement = new NoOpMemberManagement();
        messageHistory = new EmptyMessageHistory();
    }

    @Override public String id() { return "telegram"; }
    @Override public Messaging messaging() { return messaging; }
    @Override public Threading threading() { return threading; }
    @Override public Discovery discovery() { return discovery; }
    @Override public Reactions reactions() { return reactions; }
    @Override public Presence presence() { return presence; }
    @Override public Members members() { return members; }
    @Override public ChannelManagement channelManagement() { return channelManagement; }
    @Override public MemberManagement memberManagement() { return memberManagement; }
    @Override public MessageHistory messageHistory() { return messageHistory; }
    @Override public Commands commands() { return commands; }

    @Override
    public boolean supports(final Class<?> capability) {
        return activeCapabilities.contains(capability);
    }

    public void registerKnownChat(final TelegramChat chat) {
        knownChats.put(String.valueOf(chat.id()), chat);
    }

    private SendResult sendMessage(final ChatChannelRef channel, final ChatContent content) {
        final String text = content.markdown() != null ? content.markdown() : content.text();
        final TelegramMessage msg = client.sendMessage(token, channel.id(), text, null);
        if (msg == null) {
            return SendResult.failure("Telegram send failed");
        }
        return SendResult.success(
                new ChatMessageRef(channel, String.valueOf(msg.messageId())),
                Instant.now());
    }

    private SendResult replyToMessage(final ChatMessageRef parent, final ChatContent content) {
        final String text = content.markdown() != null ? content.markdown() : content.text();
        final long replyToId = Long.parseLong(parent.messageId());
        final TelegramMessage msg = client.sendMessage(token, parent.channel().id(), text, replyToId);
        if (msg == null) {
            return SendResult.failure("Telegram reply failed");
        }
        return SendResult.success(
                new ChatMessageRef(parent.channel(), String.valueOf(msg.messageId())),
                Instant.now());
    }

    private List<Channel> listChannels() {
        final List<Channel> result = new ArrayList<>();
        for (final var entry : knownChats.entrySet()) {
            final String chatId = entry.getKey();
            final TelegramChat cached = entry.getValue();
            final TelegramChat chat = client.getChat(token, chatId);
            final int memberCount = client.getChatMemberCount(token, chatId);
            final TelegramChat effective = chat != null ? chat : cached;
            final String name = effective.title() != null ? effective.title()
                    : effective.firstName() != null ? effective.firstName() : chatId;
            result.add(new Channel(
                    new ChatChannelRef(chatId),
                    name,
                    effective.description(),
                    null,
                    "private".equals(effective.type()),
                    memberCount > 0 ? memberCount : null));
        }
        return result;
    }

    private List<Member> listMembers(final ChatChannelRef channel) {
        final var admins = client.getChatAdministrators(token, channel.id());
        return admins.stream()
                .map(cm -> new Member(
                        new MemberRef(String.valueOf(cm.user().id())),
                        cm.user().firstName() != null ? cm.user().firstName() : String.valueOf(cm.user().id())))
                .toList();
    }

    private class TelegramReactions implements Reactions {
        @Override
        public void add(final ChatMessageRef message, final String emoji) {
            client.setMessageReaction(token, message.channel().id(),
                    Long.parseLong(message.messageId()), emoji);
        }

        @Override
        public void remove(final ChatMessageRef message, final String emoji) {
            client.setMessageReaction(token, message.channel().id(),
                    Long.parseLong(message.messageId()), null);
        }

        @Override
        public List<String> list(final ChatMessageRef message) {
            return Collections.emptyList();
        }
    }
}
