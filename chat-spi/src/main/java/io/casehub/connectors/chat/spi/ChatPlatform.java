package io.casehub.connectors.chat.spi;

import io.casehub.connectors.chat.degraded.ChannelFallbackThreading;
import io.casehub.connectors.chat.degraded.EmptyDiscovery;
import io.casehub.connectors.chat.degraded.EmptyMembers;
import io.casehub.connectors.chat.degraded.EmptyMessageHistory;
import io.casehub.connectors.chat.degraded.NoOpChannelManagement;
import io.casehub.connectors.chat.degraded.NoOpCommands;
import io.casehub.connectors.chat.degraded.NoOpMemberManagement;
import io.casehub.connectors.chat.degraded.NoOpReactions;
import io.casehub.connectors.chat.degraded.UnknownPresence;
import io.casehub.connectors.PlatformExtensible;
import io.casehub.platform.simulation.SimulationEligible;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@SimulationEligible(name = "chat-platform",
                    capabilities = {"messaging", "threading", "discovery", "reactions",
                                    "presence", "members", "channelManagement",
                                    "memberManagement", "messageHistory", "commands"})
public interface ChatPlatform extends PlatformExtensible {

    String id();

    Messaging messaging();

    Threading threading();

    Discovery discovery();

    Reactions reactions();

    Presence presence();

    Members members();

    ChannelManagement channelManagement();

    MemberManagement memberManagement();

    MessageHistory messageHistory();

    Commands commands();

    boolean supports(Class<?> capability);

    static Builder builder(final String id) {
        return new Builder(id);
    }

    class Builder {
        private final String            id;
        private       Messaging         messaging;
        private       Threading         threading;
        private       Discovery         discovery;
        private       Reactions         reactions;
        private       Presence          presence;
        private       Members           members;
        private       ChannelManagement channelManagement;
        private       MemberManagement  memberManagement;
        private       MessageHistory    messageHistory;
        private       Commands          commands;

        private final Set<Class<?>> nativeCapabilities = new HashSet<>();

        Builder(final String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder messaging(final Messaging m)                  {
                                                                         this.messaging = m;
                                                                         nativeCapabilities.add(Messaging.class);
                                                                         return this;
                                                                     }

        public Builder threading(final Threading t)                  {
                                                                         this.threading = t;
                                                                         nativeCapabilities.add(Threading.class);
                                                                         return this;
                                                                     }

        public Builder discovery(final Discovery d)                  {
                                                                         this.discovery = d;
                                                                         nativeCapabilities.add(Discovery.class);
                                                                         return this;
                                                                     }

        public Builder reactions(final Reactions r)                  {
                                                                         this.reactions = r;
                                                                         nativeCapabilities.add(Reactions.class);
                                                                         return this;
                                                                     }

        public Builder presence(final Presence p)                    {
                                                                         this.presence = p;
                                                                         nativeCapabilities.add(Presence.class);
                                                                         return this;
                                                                     }

        public Builder members(final Members m)                      {
                                                                         this.members = m;
                                                                         nativeCapabilities.add(Members.class);
                                                                         return this;
                                                                     }

        public Builder channelManagement(final ChannelManagement cm) {
                                                                         this.channelManagement = cm;
                                                                         nativeCapabilities.add(ChannelManagement.class);
                                                                         return this;
                                                                     }

        public Builder memberManagement(final MemberManagement mm)   {
                                                                         this.memberManagement = mm;
                                                                         nativeCapabilities.add(MemberManagement.class);
                                                                         return this;
                                                                     }

        public Builder messageHistory(final MessageHistory mh)       {
                                                                         this.messageHistory = mh;
                                                                         nativeCapabilities.add(MessageHistory.class);
                                                                         return this;
                                                                     }

        public Builder commands(final Commands c)                    {
                                                                         this.commands = c;
                                                                         nativeCapabilities.add(Commands.class);
                                                                         return this;
                                                                     }


        public ChatPlatform build() {
            Objects.requireNonNull(messaging, "messaging is required");
            return new DefaultChatPlatform(
                    id,
                    messaging,
                    threading != null ? threading : new ChannelFallbackThreading(messaging),
                    discovery != null ? discovery : new EmptyDiscovery(),
                    reactions != null ? reactions : new NoOpReactions(),
                    presence != null ? presence : new UnknownPresence(),
                    members != null ? members : new EmptyMembers(),
                    channelManagement != null ? channelManagement : new NoOpChannelManagement(),
                    memberManagement != null ? memberManagement : new NoOpMemberManagement(),
                    messageHistory != null ? messageHistory : new EmptyMessageHistory(),
                    commands != null ? commands : new NoOpCommands(),
                    Set.copyOf(nativeCapabilities));
        }
    }
}
