package io.casehub.connectors.chat.irc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ChatIrcBeans {

    @Produces
    @ApplicationScoped
    public IrcChatPlatform ircChatPlatform(IrcClient client) {
        return new IrcChatPlatform(client);
    }

    @Produces
    @ApplicationScoped
    public IrcInboundConnector ircInboundConnector(
            IrcClient client,
            @ConfigProperty(name = "casehub.connectors.chat-irc.channels") Optional<List<String>> channels) {
        return new IrcInboundConnector(client, channels);
    }

    @Produces
    @ApplicationScoped
    public IrcInboundTranslator ircInboundTranslator() {
        return new IrcInboundTranslator();
    }
}
