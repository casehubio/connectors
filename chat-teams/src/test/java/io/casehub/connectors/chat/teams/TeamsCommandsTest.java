package io.casehub.connectors.chat.teams;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.chat.command.CommandDefinition;

class TeamsCommandsTest {

    @Test
    void registerAll_doesNotThrow() {
        var cmds = new TeamsCommands();
        cmds.registerAll(List.of(
                new CommandDefinition("help", "Shows help", List.of()),
                new CommandDefinition("status", "Shows status", List.of())));
    }

    @Test
    void registerAll_emptyList_doesNotThrow() {
        var cmds = new TeamsCommands();
        cmds.registerAll(List.of());
    }
}
