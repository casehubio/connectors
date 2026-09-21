package io.casehub.connectors.chat;

import io.casehub.connectors.chat.degraded.ChannelFallbackThreading;
import io.casehub.connectors.chat.degraded.EmptyDiscovery;
import io.casehub.connectors.chat.degraded.EmptyMembers;
import io.casehub.connectors.chat.degraded.EmptyMessageHistory;
import io.casehub.connectors.chat.degraded.NoOpChannelManagement;
import io.casehub.connectors.chat.degraded.NoOpMemberManagement;
import io.casehub.connectors.chat.degraded.NoOpReactions;
import io.casehub.connectors.chat.degraded.UnknownPresence;
import io.casehub.connectors.chat.model.SendResult;
import io.casehub.connectors.chat.spi.ChannelManagement;
import io.casehub.connectors.chat.spi.ChatPlatform;
import io.casehub.connectors.chat.spi.Discovery;
import io.casehub.connectors.chat.spi.MemberManagement;
import io.casehub.connectors.chat.spi.Members;
import io.casehub.connectors.chat.spi.MessageHistory;
import io.casehub.connectors.chat.spi.Messaging;
import io.casehub.connectors.chat.spi.Presence;
import io.casehub.connectors.chat.spi.Reactions;
import io.casehub.connectors.chat.spi.Threading;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpChatPlatform implements ChatPlatform {

    @Override
    public String id() {
        return "none";
    }

    @Override
    public Messaging messaging() {
        return (channel, content) -> SendResult.failure("No chat provider configured");
    }

    @Override
    public Threading threading() {
        return new ChannelFallbackThreading(messaging());
    }

    @Override
    public Discovery discovery() {
        return new EmptyDiscovery();
    }

    @Override
    public Reactions reactions() {
        return new NoOpReactions();
    }

    @Override
    public Presence presence() {
        return new UnknownPresence();
    }

    @Override
    public Members members() {
        return new EmptyMembers();
    }

    @Override
    public ChannelManagement channelManagement() {
        return new NoOpChannelManagement();
    }

    @Override
    public MemberManagement memberManagement() {
        return new NoOpMemberManagement();
    }

    @Override
    public MessageHistory messageHistory() {
        return new EmptyMessageHistory();
    }

    @Override
    public boolean supports(final Class<?> capability) {
        return false;
    }
}
