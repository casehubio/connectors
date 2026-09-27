package io.casehub.connectors.chat.command;

import io.casehub.connectors.chat.spi.ChatPlatform;
import io.casehub.connectors.chat.spi.Commands;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CommandService {

    private static final Logger LOG =
            Logger.getLogger(CommandService.class.getName());

    private final Map<String, CommandHandler> handlers;
    private final List<CommandDefinition> definitions;

    public CommandService(
            List<CommandHandler> handlers,
            List<ChatPlatform> platforms) {

        this.handlers = handlers.stream()
                .collect(Collectors.toMap(
                        h -> h.definition().name(),
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate command name: '"
                                    + a.definition().name() + "'");
                        }));

        this.definitions = handlers.stream()
                .map(CommandHandler::definition)
                .toList();

        if (!definitions.isEmpty()) {
            for (ChatPlatform platform : platforms) {
                if (platform.supports(Commands.class)) {
                    try {
                        platform.commands().registerAll(definitions);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING,
                                "Command registration failed on "
                                + platform.id(), e);
                    }
                }
            }
        }
    }

    public CommandResponse dispatch(
            String commandName,
            CommandInvocation invocation) {
        CommandHandler handler = handlers.get(commandName);
        if (handler == null) {
            LOG.warning("No handler for command: " + commandName);
            return new CommandResponse.Immediate(
                    "Unknown command: " + commandName, true);
        }
        return handler.handle(invocation);
    }

    public List<CommandDefinition> definitions() {
        return definitions;
    }
}
