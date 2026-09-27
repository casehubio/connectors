package io.casehub.connectors.chat.command;

import java.util.Map;

public record CommandInvocation(
        String commandName,
        Map<String, String> arguments,
        String userId,
        String channelId,
        String platformId,
        Map<String, String> metadata) {

    public CommandInvocation {
        if (arguments == null) arguments = Map.of();
        if (metadata == null) metadata = Map.of();
    }

    public String argument(String name) {
        return arguments.get(name);
    }

    public String requireArgument(String name) {
        String value = arguments.get(name);
        if (value == null)
            throw new IllegalArgumentException("Missing required argument: " + name);
        return value;
    }
}
