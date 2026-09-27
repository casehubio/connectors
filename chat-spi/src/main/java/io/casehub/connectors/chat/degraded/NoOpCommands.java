package io.casehub.connectors.chat.degraded;

import io.casehub.connectors.chat.command.CommandDefinition;
import io.casehub.connectors.chat.spi.Commands;
import java.util.List;
import java.util.logging.Logger;

public class NoOpCommands implements Commands {
    private static final Logger LOG =
            Logger.getLogger(NoOpCommands.class.getName());

    @Override
    public void registerAll(List<CommandDefinition> commands) {
        if (!commands.isEmpty()) {
            LOG.warning("Commands capability not supported — "
                    + commands.size() + " commands not registered");
        }
    }
}
