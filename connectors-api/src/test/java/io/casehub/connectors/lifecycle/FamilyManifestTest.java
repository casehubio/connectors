package io.casehub.connectors.lifecycle;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FamilyManifestTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
            .build();

    @Test
    void parses_google_family_manifest() throws Exception {
        var yaml = """
                family:
                  id: google
                  description: "Google Workspace"
                  consent: incremental
                  oauth:
                    auth-url: "https://accounts.google.com/o/oauth2/v2/auth"
                    token-url: "https://oauth2.googleapis.com/token"
                    redirect-uri: "{callback-base-url}/oauth/callback/google"
                    scopes:
                      - "https://www.googleapis.com/auth/calendar.readonly"
                      - "https://www.googleapis.com/auth/drive.readonly"
                    access-type: offline
                    prompt: consent
                  fields:
                    - name: client-id
                      sensitive: false
                      description: "Google OAuth2 client ID"
                    - name: client-secret
                      description: "Google OAuth2 client secret"
                """;
        var family = MAPPER.readValue(yaml, FamilyManifest.class);
        assertThat(family.family().id()).isEqualTo("google");
        assertThat(family.family().consent()).isEqualTo(FamilyManifest.ConsentStrategy.INCREMENTAL);
        assertThat(family.family().oauth().authUrl()).isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(family.family().oauth().tokenUrl()).isEqualTo("https://oauth2.googleapis.com/token");
        assertThat(family.family().oauth().scopes()).containsExactly(
                "https://www.googleapis.com/auth/calendar.readonly",
                "https://www.googleapis.com/auth/drive.readonly");
        assertThat(family.family().oauth().accessType()).isEqualTo("offline");
        assertThat(family.family().oauth().prompt()).isEqualTo("consent");
        assertThat(family.family().fields()).hasSize(2);
        assertThat(family.family().fields().get(0).name()).isEqualTo("client-id");
        assertThat(family.family().fields().get(0).isSensitive()).isFalse();
        assertThat(family.family().fields().get(1).name()).isEqualTo("client-secret");
        assertThat(family.family().fields().get(1).isSensitive()).isTrue();
    }

    @Test
    void parses_microsoft_family_manifest_with_aggregated_consent() throws Exception {
        var yaml = """
                family:
                  id: microsoft
                  description: "Microsoft 365"
                  consent: aggregated
                  oauth:
                    auth-url: "https://login.microsoftonline.com/{tenant-id}/oauth2/v2/authorize"
                    token-url: "https://login.microsoftonline.com/{tenant-id}/oauth2/v2/token"
                    redirect-uri: "{callback-base-url}/oauth/callback/microsoft"
                    scopes:
                      - "Calendars.ReadWrite"
                      - "Mail.Read"
                  fields:
                    - name: tenant-id
                      sensitive: false
                      description: "Azure AD tenant ID"
                    - name: client-id
                      sensitive: false
                      description: "Azure AD app client ID"
                    - name: client-secret
                      description: "Azure AD client secret"
                """;
        var family = MAPPER.readValue(yaml, FamilyManifest.class);
        assertThat(family.family().consent()).isEqualTo(FamilyManifest.ConsentStrategy.AGGREGATED);
        assertThat(family.family().fields()).hasSize(3);
        assertThat(family.family().fields().get(0).name()).isEqualTo("tenant-id");
    }

    @Test
    void constructor_arg_value_variant_works() {
        var arg = new ConnectorManifest.ConstructorArg(null, null, "literal-string");
        assertThat(arg.value()).isEqualTo("literal-string");
        assertThat(arg.field()).isNull();
        assertThat(arg.bean()).isNull();
    }

    @Test
    void constructor_arg_rejects_zero_set() {
        assertThatThrownBy(() -> new ConnectorManifest.ConstructorArg(null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void constructor_arg_rejects_two_set() {
        assertThatThrownBy(() -> new ConnectorManifest.ConstructorArg("field", "bean", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void constructor_arg_rejects_field_and_value() {
        assertThatThrownBy(() -> new ConnectorManifest.ConstructorArg("field", null, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void bean_declaration_qualifier_parses() throws Exception {
        var yaml = """
                connector:
                  id: test
                  platform: test
                  produces:
                    - class: com.example.Foo
                      type: platform
                      qualifier: my-qualifier
                      constructor-args:
                        - value: hello
                """;
        var manifest = MAPPER.readValue(yaml, ConnectorManifest.class);
        assertThat(manifest.connector().produces().get(0).qualifier()).isEqualTo("my-qualifier");
        assertThat(manifest.connector().produces().get(0).constructorArgs().get(0).value()).isEqualTo("hello");
    }

    @Test
    void credentialKey_returns_family_when_set() {
        var auth = new ConnectorManifest.AuthDeclaration("oauth2", "google", null, null);
        var decl = new ConnectorManifest.ConnectorDeclaration(
                "calendar-google", "calendar", "desc", auth, null, null, null, null, null);
        assertThat(decl.credentialKey()).isEqualTo("google");
    }

    @Test
    void credentialKey_returns_id_when_no_family() {
        var auth = new ConnectorManifest.AuthDeclaration("api-key", null, null, null);
        var decl = new ConnectorManifest.ConnectorDeclaration(
                "numbrrs", "mobile", "desc", auth, null, null, null, null, null);
        assertThat(decl.credentialKey()).isEqualTo("numbrrs");
    }

    @Test
    void credentialKey_returns_id_when_no_auth() {
        var decl = new ConnectorManifest.ConnectorDeclaration(
                "some-id", "test", "desc", null, null, null, null, null, null);
        assertThat(decl.credentialKey()).isEqualTo("some-id");
    }

    @Test
    void auth_declaration_parses_inline_oauth() throws Exception {
        var yaml = """
                connector:
                  id: github
                  platform: project
                  auth:
                    type: oauth2
                    oauth:
                      auth-url: "https://github.com/login/oauth/authorize"
                      token-url: "https://github.com/login/oauth/access_token"
                      redirect-uri: "{callback-base-url}/oauth/callback/github"
                      scopes:
                        - "repo"
                        - "project"
                    fields:
                      - name: client-id
                        sensitive: false
                        description: "GitHub OAuth client ID"
                """;
        var manifest = MAPPER.readValue(yaml, ConnectorManifest.class);
        assertThat(manifest.connector().auth().oauth()).isNotNull();
        assertThat(manifest.connector().auth().oauth().authUrl()).isEqualTo("https://github.com/login/oauth/authorize");
        assertThat(manifest.connector().auth().oauth().scopes()).containsExactly("repo", "project");
        assertThat(manifest.connector().auth().family()).isNull();
    }
}
