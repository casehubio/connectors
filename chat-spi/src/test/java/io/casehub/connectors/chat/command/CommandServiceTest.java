package io.casehub.connectors.chat.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.chat.degraded.NoOpCommands;
import io.casehub.connectors.chat.spi.Commands;

class CommandServiceTest {

    static CommandHandler handler(String name, String desc, String response) {
        return new CommandHandler() {
            @Override
            public CommandDefinition definition() {
                return new CommandDefinition(name, desc, List.of());
            }
            @Override
            public CommandResponse handle(CommandInvocation invocation) {
                return new CommandResponse.Immediate(response);
            }
        };
    }

    static StubPlatform platform(String id, Commands commands, boolean supports) {
        return new StubPlatform(id, commands, supports);
    }

    @Test
    void dispatchRoutesToCorrectHandler() {
        var service = new CommandService(
                List.of(handler("ping", "Ping", "pong"),
                        handler("status", "Status", "ok")),
                List.of());

        var invocation = new CommandInvocation(
                "ping", Map.of(), "u1", "ch1", "test", Map.of());
        var result = service.dispatch("ping", invocation);

        assertThat(result).isInstanceOf(CommandResponse.Immediate.class);
        assertThat(((CommandResponse.Immediate) result).text()).isEqualTo("pong");
    }

    @Test
    void dispatchUnknownCommandReturnsError() {
        var service = new CommandService(
                List.of(handler("ping", "Ping", "pong")),
                List.of());

        var invocation = new CommandInvocation(
                "nope", Map.of(), "u1", "ch1", "test", Map.of());
        var result = service.dispatch("nope", invocation);

        assertThat(result).isInstanceOf(CommandResponse.Immediate.class);
        assertThat(((CommandResponse.Immediate) result).text())
                .contains("Unknown command");
    }

    @Test
    void duplicateCommandNamesThrow() {
        assertThatThrownBy(() -> new CommandService(
                List.of(handler("ping", "Ping1", "a"),
                        handler("ping", "Ping2", "b")),
                List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate command name");
    }

    @Test
    void definitionsReturnsAllHandlerDefinitions() {
        var service = new CommandService(
                List.of(handler("ping", "Ping", "pong"),
                        handler("status", "Status", "ok")),
                List.of());

        assertThat(service.definitions()).hasSize(2);
        assertThat(service.definitions().stream()
                .map(CommandDefinition::name).toList())
                .containsExactlyInAnyOrder("ping", "status");
    }

    @Test
    void registersCommandsOnSupportingPlatforms() {
        var registered = new ArrayList<List<CommandDefinition>>();
        Commands capturing = registered::add;
        var supportingPlatform = platform("discord", capturing, true);
        var unsupportingPlatform = platform("irc", new NoOpCommands(), false);

        new CommandService(
                List.of(handler("ping", "Ping", "pong")),
                List.of(supportingPlatform, unsupportingPlatform));

        assertThat(registered).hasSize(1);
        assertThat(registered.get(0)).hasSize(1);
        assertThat(registered.get(0).get(0).name()).isEqualTo("ping");
    }

    @Test
    void registrationFailureDoesNotBreakStartup() {
        Commands failing = commands -> {
            throw new RuntimeException("API down");
        };
        var platform = platform("discord", failing, true);

        var service = new CommandService(
                List.of(handler("ping", "Ping", "pong")),
                List.of(platform));

        assertThat(service.definitions()).hasSize(1);
    }

    @Test
    void emptyHandlerListSkipsRegistration() {
        var registered = new ArrayList<List<CommandDefinition>>();
        Commands capturing = registered::add;
        var platform = platform("discord", capturing, true);

        new CommandService(List.of(), List.of(platform));

        assertThat(registered).isEmpty();
    }
}
