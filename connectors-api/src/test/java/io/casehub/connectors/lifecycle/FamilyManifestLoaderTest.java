package io.casehub.connectors.lifecycle;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FamilyManifestLoaderTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
            .build();

    @Test
    void enrichWithFamily_populates_member_auth_fields() {
        var family = parseFamily("""
                family:
                  id: test-fam
                  description: "Test family"
                  consent: aggregated
                  oauth:
                    auth-url: "https://auth.example.com/authorize"
                    token-url: "https://auth.example.com/token"
                    redirect-uri: "{callback-base-url}/oauth/callback/test-fam"
                    scopes:
                      - "scope-a"
                      - "scope-b"
                  fields:
                    - name: client-id
                      sensitive: false
                      description: "Client ID"
                    - name: client-secret
                      description: "Client secret"
                """);

        var member = parseConnector("""
                connector:
                  id: member-one
                  platform: test
                  auth:
                    type: oauth2
                    family: test-fam
                  produces: []
                """);

        var families = Map.of("test-fam", family);
        var enriched = ConnectorManifestLoader.resolveFamilies(List.of(member), families);

        assertThat(enriched).hasSize(1);
        var result = enriched.get(0);
        assertThat(result.connector().auth().fields()).hasSize(2);
        assertThat(result.connector().auth().fields().get(0).name()).isEqualTo("client-id");
        assertThat(result.connector().auth().fields().get(1).name()).isEqualTo("client-secret");
        assertThat(result.connector().auth().oauth()).isNotNull();
        assertThat(result.connector().auth().oauth().authUrl()).isEqualTo("https://auth.example.com/authorize");
        assertThat(result.connector().auth().oauth().scopes()).containsExactly("scope-a", "scope-b");
        assertThat(result.connector().credentialKey()).isEqualTo("test-fam");
    }

    @Test
    void enrichWithFamily_does_not_modify_non_family_connector() {
        var standalone = parseConnector("""
                connector:
                  id: standalone
                  platform: test
                  auth:
                    type: api-key
                    fields:
                      - name: api-key
                        description: "API key"
                  produces: []
                """);

        var enriched = ConnectorManifestLoader.resolveFamilies(List.of(standalone), Map.of());

        assertThat(enriched).hasSize(1);
        var result = enriched.get(0);
        assertThat(result.connector().auth().fields()).hasSize(1);
        assertThat(result.connector().auth().fields().get(0).name()).isEqualTo("api-key");
        assertThat(result.connector().auth().oauth()).isNull();
        assertThat(result.connector().credentialKey()).isEqualTo("standalone");
    }

    @Test
    void enrichWithFamily_warns_on_missing_family_reference() {
        var orphan = parseConnector("""
                connector:
                  id: orphan
                  platform: test
                  auth:
                    type: oauth2
                    family: nonexistent
                  produces: []
                """);

        var enriched = ConnectorManifestLoader.resolveFamilies(List.of(orphan), Map.of());

        assertThat(enriched).hasSize(1);
        assertThat(enriched.get(0).connector().id()).isEqualTo("orphan");
        assertThat(enriched.get(0).connector().auth().fields()).isNullOrEmpty();
    }

    @Test
    void discover_from_classpath_includes_test_family() {
        var families = FamilyManifestLoader.discover();
        assertThat(families).containsKey("test-family");
        var fam = families.get("test-family");
        assertThat(fam.family().consent()).isEqualTo(FamilyManifest.ConsentStrategy.AGGREGATED);
    }

    private FamilyManifest parseFamily(String yaml) {
        try {
            return MAPPER.readValue(yaml, FamilyManifest.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ConnectorManifest parseConnector(String yaml) {
        try {
            return MAPPER.readValue(yaml, ConnectorManifest.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
