package io.casehub.connectors.chat.command;

import java.util.List;

public record CommandDefinition(
        String name,
        String description,
        List<CommandParameter> parameters) {

    public CommandDefinition {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Command name must not be blank");
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("Command description must not be blank");
        if (parameters == null)
            parameters = List.of();
    }
}
