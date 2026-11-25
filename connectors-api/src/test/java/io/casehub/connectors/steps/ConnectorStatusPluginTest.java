package io.casehub.connectors.steps;

import io.casehub.connectors.lifecycle.ConnectorManifest;
import io.casehub.connectors.lifecycle.ConnectorManifest.AuthDeclaration;
import io.casehub.connectors.lifecycle.ConnectorManifest.ConnectorDeclaration;
import io.casehub.connectors.lifecycle.ConnectorRegistry;
import io.casehub.connectors.lifecycle.ConnectorStatus;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorStatusPluginTest {

    private static ConnectorManifest manifest(String id) {
        return new ConnectorManifest(new ConnectorDeclaration(
                id, "test", "Test " + id,
                new AuthDeclaration("api-key", null, List.of()), List.of(), null, null, null, null));
    }

    @Test
    void passes_when_status_matches() {
        var registry = new ConnectorRegistry();
        registry.register(manifest("numbrrs"), ConnectorStatus.READY);

        var plugin = new ConnectorStatusPlugin("numbrrs", "READY");
        var result = plugin.execute(registry);

        assertThat(result).isInstanceOf(Result.Success.class);
        var output = ((Result.Success) result).output();
        assertThat(output.get("connector-id")).isEqualTo("numbrrs");
        assertThat(output.get("status")).isEqualTo("READY");
    }

    @Test
    void fails_when_status_does_not_match() {
        var registry = new ConnectorRegistry();
        registry.register(manifest("signal"), ConnectorStatus.UNCONFIGURED);

        var plugin = new ConnectorStatusPlugin("signal", "READY");
        var result = plugin.execute(registry);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure) result).message()).contains("UNCONFIGURED").contains("READY");
    }

    @Test
    void fails_for_unknown_connector() {
        var registry = new ConnectorRegistry();

        var plugin = new ConnectorStatusPlugin("unknown", "READY");
        var result = plugin.execute(registry);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure) result).message()).contains("Unknown connector");
    }
}
