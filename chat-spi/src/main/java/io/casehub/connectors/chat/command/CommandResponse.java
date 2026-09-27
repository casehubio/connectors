package io.casehub.connectors.chat.command;

import java.util.function.Consumer;

public sealed interface CommandResponse {

    record Immediate(String text, boolean ephemeral)
            implements CommandResponse {
        public Immediate(String text) {
            this(text, false);
        }
    }

    record Deferred(boolean ephemeral, Consumer<DeferredReply> callback)
            implements CommandResponse {}
}
