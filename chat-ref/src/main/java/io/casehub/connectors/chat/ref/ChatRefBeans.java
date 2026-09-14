package io.casehub.connectors.chat.ref;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ChatRefBeans {

    @Produces
    @ApplicationScoped
    public RefChatPlatform refChatPlatform(ChatBackend backend) {
        return new RefChatPlatform(backend);
    }
}
