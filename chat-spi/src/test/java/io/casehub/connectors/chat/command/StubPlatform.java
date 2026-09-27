package io.casehub.connectors.chat.command;

import io.casehub.connectors.chat.degraded.ChannelFallbackThreading;
import io.casehub.connectors.chat.degraded.EmptyDiscovery;
import io.casehub.connectors.chat.degraded.EmptyMembers;
import io.casehub.connectors.chat.degraded.EmptyMessageHistory;
import io.casehub.connectors.chat.degraded.NoOpChannelManagement;
import io.casehub.connectors.chat.degraded.NoOpCommands;
import io.casehub.connectors.chat.degraded.NoOpMemberManagement;
import io.casehub.connectors.chat.degraded.NoOpReactions;
import io.casehub.connectors.chat.degraded.UnknownPresence;
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

class StubPlatform implements ChatPlatform {
    private final String id;
    private final Commands commands;
    private final boolean supportsCommands;

    StubPlatform(String id, Commands commands, boolean supportsCommands) {
        this.id = id;
        this.commands = commands;
        this.supportsCommands = supportsCommands;
    }

    @Override public String id() { return id; }
    @Override public Messaging messaging() {
        return (ch, c) -> SendResult.failure("stub");
    }
    @Override public Threading threading() { return new ChannelFallbackThreading(messaging()); }
    @Override public Discovery discovery() { return new EmptyDiscovery(); }
    @Override public Reactions reactions() { return new NoOpReactions(); }
    @Override public Presence presence() { return new UnknownPresence(); }
    @Override public Members members() { return new EmptyMembers(); }
    @Override public ChannelManagement channelManagement() { return new NoOpChannelManagement(); }
    @Override public MemberManagement memberManagement() { return new NoOpMemberManagement(); }
    @Override public MessageHistory messageHistory() { return new EmptyMessageHistory(); }
    @Override public Commands commands() { return commands; }
    @Override public boolean supports(Class<?> cap) {
        return cap == Commands.class && supportsCommands;
    }
}
