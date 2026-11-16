package io.casehub.connectors.chat.teams;

public sealed interface InvokeResponse {

    record Accepted() implements InvokeResponse {}

    record CardUpdate(String cardJson) implements InvokeResponse {}

    record Message(String text) implements InvokeResponse {}

    record TaskModuleContinue(String title, String cardJson) implements InvokeResponse {}

    record TaskModuleMessage(String text) implements InvokeResponse {}

    record Error(int statusCode, String message) implements InvokeResponse {}
}
