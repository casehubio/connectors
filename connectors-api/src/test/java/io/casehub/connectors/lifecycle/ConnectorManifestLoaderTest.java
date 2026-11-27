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

    @Test
    void resolveFamilies_enriches_all_google_family_members() {
        var calendarYaml = "connector:\n  id: calendar-google\n  platform: calendar\n  description: Google Calendar\n  auth:\n    type: oauth2\n    family: google\n  provides:\n    - capability: calendar\n";
        var documentYaml = "connector:\n  id: document-google\n  platform: document\n  description: Google Drive\n  auth:\n    type: oauth2\n    family: google\n  provides:\n    - capability: document\n      sub: [files, folders, search, sharing]\n";
        var contactsYaml = "connector:\n  id: contacts-google\n  platform: contacts\n  description: Google Contacts\n  auth:\n    type: oauth2\n    family: google\n  provides:\n    - capability: contacts\n      sub: [read, groups, write]\n";
        var emailYaml = "connector:\n  id: email-google\n  platform: email\n  description: Gmail\n  auth:\n    type: oauth2\n    family: google\n  provides:\n    - capability: email\n      sub: [receive, search]\n";

        var connectors = List.of(parse(calendarYaml), parse(documentYaml), parse(contactsYaml), parse(emailYaml));
        var families = FamilyManifestLoader.discover();

        var resolved = ConnectorManifestLoader.resolveFamilies(connectors, families);

        assertThat(resolved).hasSize(4);
        for (var m : resolved) {
            assertThat(m.connector().auth().family()).isEqualTo("google");
            assertThat(m.connector().auth().oauth()).isNotNull();
            assertThat(m.connector().auth().oauth().authUrl()).contains("accounts.google.com");
            assertThat(m.connector().credentialKey()).isEqualTo("google");
        }

        var byId = resolved.stream().collect(java.util.stream.Collectors.toMap(m -> m.connector().id(), m -> m));
        assertThat(byId.get("calendar-google").connector().provides()).extracting(ConnectorManifest.CapabilityDeclaration::capability).containsExactly("calendar");
        assertThat(byId.get("document-google").connector().provides()).extracting(ConnectorManifest.CapabilityDeclaration::capability).containsExactly("document");
        assertThat(byId.get("contacts-google").connector().provides()).extracting(ConnectorManifest.CapabilityDeclaration::capability).containsExactly("contacts");
        assertThat(byId.get("email-google").connector().provides()).extracting(ConnectorManifest.CapabilityDeclaration::capability).containsExactly("email");
    }

    private ConnectorManifest parse(String yaml) {
        try {
            return MAPPER.readValue(yaml, ConnectorManifest.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
