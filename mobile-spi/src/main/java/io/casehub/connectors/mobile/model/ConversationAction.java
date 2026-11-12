package io.casehub.connectors.mobile.model;

public sealed interface ConversationAction {

    record Hold() implements ConversationAction {
    }

    record Transfer(String transferTo) implements ConversationAction {
    }

    record HangUp() implements ConversationAction {
    }
}
