package io.casehub.connectors.chat.spi;

import io.casehub.connectors.chat.command.CommandDefinition;
import java.util.List;

public interface Commands {
    void registerAll(List<CommandDefinition> commands);
}
