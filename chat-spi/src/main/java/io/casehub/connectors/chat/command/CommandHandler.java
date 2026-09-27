package io.casehub.connectors.chat.command;

public interface CommandHandler {
    CommandDefinition definition();
    CommandResponse handle(CommandInvocation invocation);
}
