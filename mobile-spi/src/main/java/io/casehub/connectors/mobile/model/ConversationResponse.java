package io.casehub.connectors.mobile.model;

public record ConversationResponse(String text, ConversationAction action) {

    public ConversationResponse(String text) {
        this(text, null);
    }
}
