package io.casehub.connectors.chat.slack;

import io.casehub.connectors.chat.command.CommandDefinition;
import io.casehub.connectors.chat.spi.Commands;
import java.util.List;
import java.util.logging.Logger;

public class SlackCommands implements Commands {
    private static final Logger LOG =
            Logger.getLogger(SlackCommands.class.getName());

    @Override
    public void registerAll(List<CommandDefinition> commands) {
        LOG.info("slack: " + commands.size()
                + " commands available (register via Slack app manifest)");
    }
}
