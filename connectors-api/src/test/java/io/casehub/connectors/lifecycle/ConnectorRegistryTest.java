package io.casehub.connectors.lifecycle;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorRegistryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String NUMBRRS_YAML = """
            connector:
              id: numbrrs
              platform: mobile
              description: "Numbrrs provider"
              auth:
                type: api-key
                fields:
                  - name: api-key
                    description: "API key"
              produces:
                - class: io.casehub.connectors.mobile.numbrrs.NumbrrsMobilePlatform
                  type: platform
            """;

    private static final String SIGNAL_YAML = """
            connector:
              id: signal
              platform: chat
              description: "Signal provider"
              auth:
                type: api-key
                fields:
                  - name: api-url
                    description: "Signal CLI API URL"
              produces:
                - class: io.casehub.connectors.chat.signal.SignalChatPlatform
                  type: platform
            """;

    @Test
    void registry_tracks_connector_status() {
        var registry = new ConnectorRegistry();
        var manifest = parse(NUMBRRS_YAML);
        registry.register(manifest, ConnectorStatus.READY);

        assertThat(registry.status("numbrrs")).isEqualTo(ConnectorStatus.READY);
        assertThat(registry.all()).hasSize(1);
        assertThat(registry.configured()).hasSize(1);
        assertThat(registry.unconfigured()).isEmpty();
    }

    @Test
    void unconfigured_connectors_reported() {
        var registry = new ConnectorRegistry();
        registry.register(parse(NUMBRRS_YAML), ConnectorStatus.READY);
        registry.register(parse(SIGNAL_YAML), ConnectorStatus.UNCONFIGURED);

        assertThat(registry.configured()).hasSize(1);
        assertThat(registry.configured().getFirst().manifest().connector().id()).isEqualTo("numbrrs");
        assertThat(registry.unconfigured()).hasSize(1);
        assertThat(registry.unconfigured().getFirst().manifest().connector().id()).isEqualTo("signal");
    }

    @Test
    void status_returns_null_for_unknown_connector() {
        var registry = new ConnectorRegistry();
        assertThat(registry.status("nonexistent")).isNull();
    }

    @Test
    void all_returns_every_registered_connector() {
        var registry = new ConnectorRegistry();
        registry.register(parse(NUMBRRS_YAML), ConnectorStatus.READY);
        registry.register(parse(SIGNAL_YAML), ConnectorStatus.ERROR);

        assertThat(registry.all()).hasSize(2);
    }

    @Test
    void manifest_returns_registered_manifest() {
        var registry = new ConnectorRegistry();
        var manifest = parse(NUMBRRS_YAML);
        registry.register(manifest, ConnectorStatus.READY);

        assertThat(registry.manifest("numbrrs")).isPresent();
        assertThat(registry.manifest("numbrrs").get().connector().platform()).isEqualTo("mobile");
        assertThat(registry.manifest("missing")).isEmpty();
    }

    @Test
    void degraded_connectors_filtered() {
        var registry = new ConnectorRegistry();
        registry.register(parse(NUMBRRS_YAML), ConnectorStatus.DEGRADED);
        registry.register(parse(SIGNAL_YAML), ConnectorStatus.READY);

        assertThat(registry.configured()).hasSize(1);
        assertThat(registry.configured().getFirst().manifest().connector().id()).isEqualTo("signal");
    }

    private ConnectorManifest parse(String yaml) {
        try {
            return MAPPER.readValue(yaml, ConnectorManifest.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
