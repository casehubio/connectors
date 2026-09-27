package io.casehub.connectors.chat.ref;

import io.casehub.connectors.chat.command.CommandDefinition;
import io.casehub.connectors.chat.spi.Commands;
import java.util.List;

public class RefCommands implements Commands {

    private List<CommandDefinition> registered = List.of();

    @Override
    public void registerAll(List<CommandDefinition> commands) {
        this.registered = List.copyOf(commands);
    }

    public List<CommandDefinition> registered() {
        return registered;
    }
}
