package io.casehub.connectors.chat.command;

public record CommandParameter(
        String name,
        String description,
        CommandParameterType type,
        boolean required) {

    public CommandParameter {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Parameter name must not be blank");
        if (type == null)
            type = CommandParameterType.STRING;
    }
}
