package io.casehub.connectors.chat.discord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.casehub.connectors.chat.command.CommandDefinition;
import io.casehub.connectors.chat.command.CommandParameterType;
import io.casehub.connectors.chat.spi.Commands;
import io.casehub.connectors.discord.DiscordClient;

import java.util.List;
import java.util.logging.Logger;

public class DiscordCommands implements Commands {

    private static final Logger LOG =
            Logger.getLogger(DiscordCommands.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DiscordClient client;
    private final String token;
    private final String applicationId;

    public DiscordCommands(DiscordClient client,
                           String token,
                           String applicationId) {
        this.client = client;
        this.token = token;
        this.applicationId = applicationId;
    }

    @Override
    public void registerAll(List<CommandDefinition> commands) {
        if (applicationId == null || applicationId.isBlank()) {
            LOG.warning("discord: application-id not configured, "
                    + "skipping command registration");
            return;
        }
        ArrayNode payload = toDiscordJson(commands);
        client.bulkOverwriteGlobalCommands(token, applicationId, payload);
        LOG.info("discord: registered " + commands.size()
                + " commands globally");
    }

    static ArrayNode toDiscordJson(List<CommandDefinition> commands) {
        var array = MAPPER.createArrayNode();
        for (var cmd : commands) {
            var node = array.addObject();
            node.put("name", cmd.name());
            node.put("description", cmd.description());
            node.put("type", 1);
            if (!cmd.parameters().isEmpty()) {
                var options = node.putArray("options");
                for (var param : cmd.parameters()) {
                    var opt = options.addObject();
                    opt.put("name", param.name());
                    opt.put("description", param.description());
                    opt.put("type", discordOptionType(param.type()));
                    opt.put("required", param.required());
                }
            }
        }
        return array;
    }

    private static int discordOptionType(CommandParameterType type) {
        return switch (type) {
            case STRING -> 3;
            case INTEGER -> 4;
            case BOOLEAN -> 5;
            case NUMBER -> 10;
        };
    }
}
