package io.casehub.connectors.chat;

import io.casehub.connectors.chat.spi.ChatPlatform;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.List;

@ApplicationScoped
public class ChatBeans {

    @Produces
    @ApplicationScoped
    public ChatPlatformService chatPlatformService(@All List<ChatPlatform> platforms) {
        return new ChatPlatformService(platforms);
    }
}
