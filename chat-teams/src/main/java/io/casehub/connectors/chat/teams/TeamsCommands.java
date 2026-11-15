package io.casehub.connectors.chat.teams;

import io.casehub.connectors.chat.command.CommandDefinition;
import io.casehub.connectors.chat.spi.Commands;

import java.util.List;
import java.util.logging.Logger;

public class TeamsCommands implements Commands {

    private static final Logger LOG = Logger.getLogger(TeamsCommands.class.getName());

    @Override
    public void registerAll(List<CommandDefinition> commands) {
        LOG.info("teams: " + commands.size()
                + " commands available (register via Azure Bot app manifest)");
    }
}
