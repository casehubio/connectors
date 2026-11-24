package io.casehub.connectors.lifecycle;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorManifestLoaderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void discovers_manifest_from_classpath() {
        var manifests = ConnectorManifestLoader.discoverFromClasspath();
        assertThat(manifests).isNotEmpty();
        assertThat(manifests).anyMatch(m -> m.connector().id().equals("test-fixture"));
    }

    @Test
    void classpath_manifest_has_correct_fields() {
        var manifests = ConnectorManifestLoader.discoverFromClasspath();
        var fixture = manifests.stream()
                .filter(m -> m.connector().id().equals("test-fixture"))
                .findFirst().orElseThrow();
        assertThat(fixture.connector().platform()).isEqualTo("test");
        assertThat(fixture.connector().auth().type()).isEqualTo("api-key");
        assertThat(fixture.connector().auth().fields()).hasSize(1);
        assertThat(fixture.connector().produces()).hasSize(1);
    }

    @Test
    void later_manifest_overrides_earlier_by_id() {
        var first = parse("connector:\n  id: x\n  platform: chat\n  auth:\n    type: api-key\n    fields: []\n  produces: []\n");
        var second = parse("connector:\n  id: x\n  platform: mobile\n  auth:\n    type: bot-token\n    fields: []\n  produces: []\n");
        var merged = ConnectorManifestLoader.merge(List.of(first, second));
        assertThat(merged).hasSize(1);
        assertThat(merged.getFirst().connector().platform()).isEqualTo("mobile");
    }

    @Test
    void merge_preserves_order_of_first_appearance() {
        var a = parse("connector:\n  id: alpha\n  platform: chat\n  auth:\n    type: api-key\n    fields: []\n  produces: []\n");
        var b = parse("connector:\n  id: beta\n  platform: mobile\n  auth:\n    type: api-key\n    fields: []\n  produces: []\n");
        var merged = ConnectorManifestLoader.merge(List.of(a, b));
        assertThat(merged).hasSize(2);
        assertThat(merged.get(0).connector().id()).isEqualTo("alpha");
        assertThat(merged.get(1).connector().id()).isEqualTo("beta");
    }

    @Test
    void discover_includes_classpath_manifests() {
        var manifests = ConnectorManifestLoader.discover();
        assertThat(manifests).anyMatch(m -> m.connector().id().equals("test-fixture"));
    }

    private ConnectorManifest parse(String yaml) {
        try {
            return MAPPER.readValue(yaml, ConnectorManifest.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
