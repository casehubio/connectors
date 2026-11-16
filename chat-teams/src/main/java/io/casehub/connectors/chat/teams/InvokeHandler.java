package io.casehub.connectors.chat.teams;

public interface InvokeHandler {
    String name();
    InvokeResponse handle(InvokeRequest request);
}
