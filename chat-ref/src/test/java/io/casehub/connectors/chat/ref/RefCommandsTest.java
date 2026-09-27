package io.casehub.connectors.chat.ref;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.chat.command.CommandDefinition;
import io.casehub.connectors.chat.spi.Commands;

class RefCommandsTest {

    @Test
    void registerAllStoresDefinitions() {
        var ref = new RefCommands();
        var defs = List.of(
                new CommandDefinition("ping", "Ping", List.of()),
                new CommandDefinition("status", "Status", List.of()));
        ref.registerAll(defs);

        assertThat(ref.registered()).hasSize(2);
        assertThat(ref.registered().stream()
                .map(CommandDefinition::name).toList())
                .containsExactly("ping", "status");
    }

    @Test
    void registeredIsEmptyByDefault() {
        assertThat(new RefCommands().registered()).isEmpty();
    }

    @Test
    void registeredIsImmutableCopy() {
        var ref = new RefCommands();
        ref.registerAll(List.of(
                new CommandDefinition("ping", "Ping", List.of())));
        var snapshot = ref.registered();
        ref.registerAll(List.of());
        assertThat(snapshot).hasSize(1);
    }

    @Test
    void refChatPlatformSupportsCommands() {
        var backend = new InMemoryChatBackend();
        var platform = new RefChatPlatform(backend);
        assertThat(platform.supports(Commands.class)).isTrue();
        assertThat(platform.commands()).isInstanceOf(RefCommands.class);
    }
}
