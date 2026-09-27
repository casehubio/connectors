package io.casehub.connectors.chat.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CommandModelTest {

    @Test
    void definitionRejectsBlankName() {
        assertThatThrownBy(() -> new CommandDefinition("", "desc", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void definitionRejectsNullDescription() {
        assertThatThrownBy(() -> new CommandDefinition("cmd", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void definitionDefaultsNullParametersToEmpty() {
        var def = new CommandDefinition("cmd", "desc", null);
        assertThat(def.parameters()).isEmpty();
    }

    @Test
    void parameterDefaultsNullTypeToString() {
        var param = new CommandParameter("name", "desc", null, false);
        assertThat(param.type()).isEqualTo(CommandParameterType.STRING);
    }

    @Test
    void parameterRejectsBlankName() {
        assertThatThrownBy(() ->
                new CommandParameter("", "desc", CommandParameterType.STRING, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void invocationDefaultsNullMapsToEmpty() {
        var inv = new CommandInvocation("cmd", null, "user1", "ch1", "test", null);
        assertThat(inv.arguments()).isEmpty();
        assertThat(inv.metadata()).isEmpty();
    }

    @Test
    void invocationRequireArgumentThrowsOnMissing() {
        var inv = new CommandInvocation("cmd", Map.of(), "user1", "ch1", "test", Map.of());
        assertThatThrownBy(() -> inv.requireArgument("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void invocationArgumentReturnsValue() {
        var inv = new CommandInvocation("cmd", Map.of("key", "val"),
                "user1", "ch1", "test", Map.of());
        assertThat(inv.argument("key")).isEqualTo("val");
        assertThat(inv.argument("nope")).isNull();
    }

    @Test
    void immediateResponseWithDefaults() {
        var resp = new CommandResponse.Immediate("hello");
        assertThat(resp.text()).isEqualTo("hello");
        assertThat(resp.ephemeral()).isFalse();
    }

    @Test
    void immediateResponseEphemeral() {
        var resp = new CommandResponse.Immediate("secret", true);
        assertThat(resp.ephemeral()).isTrue();
    }

    @Test
    void deferredReplyDefaultSend() {
        var captured = new java.util.concurrent.atomic.AtomicReference<String>();
        DeferredReply reply = (text, eph) -> captured.set(text);
        reply.send("hello");
        assertThat(captured.get()).isEqualTo("hello");
    }
}
