package io.casehub.connectors.chat.command;

public interface DeferredReply {
    void send(String text, boolean ephemeral);

    default void send(String text) {
        send(text, false);
    }
}
