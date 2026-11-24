package io.casehub.connectors.lifecycle;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorManifestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void parses_minimal_manifest() throws Exception {
        var yaml = """
                connector:
                  id: test
                  platform: chat
                  description: "Test connector"
                  auth:
                    type: api-key
                    fields:
                      - name: api-key
                        description: "The API key"
                  produces:
                    - class: com.example.TestPlatform
                      type: platform
                """;
        var manifest = MAPPER.readValue(yaml, ConnectorManifest.class);

        assertThat(manifest.connector().id()).isEqualTo("test");
        assertThat(manifest.connector().platform()).isEqualTo("chat");
        assertThat(manifest.connector().auth().type()).isEqualTo("api-key");
        assertThat(manifest.connector().auth().fields()).hasSize(1);
        assertThat(manifest.connector().auth().fields().getFirst().name()).isEqualTo("api-key");
        assertThat(manifest.connector().auth().fields().getFirst().isRequired()).isTrue();
        assertThat(manifest.connector().auth().fields().getFirst().isSensitive()).isTrue();
        assertThat(manifest.connector().produces()).hasSize(1);
        assertThat(manifest.connector().produces().getFirst().className()).isEqualTo("com.example.TestPlatform");
    }

    @Test
    void parses_full_manifest_with_constructor_args_and_defaults() throws Exception {
        var yaml = """
                connector:
                  id: telegram
                  platform: chat
                  description: "Telegram Bot API"
                  auth:
                    type: bot-token
                    fields:
                      - name: bot-token
                        description: "Bot token"
                      - name: api-url
                        required: false
                        sensitive: false
                        default: "https://api.telegram.org"
                        description: "API base URL"
                  produces:
                    - class: com.example.BotClient
                      type: client
                      constructor-args:
                        - field: api-url
                    - class: com.example.ChatPlatform
                      type: platform
                      constructor-args:
                        - bean: com.example.BotClient
                        - field: bot-token
                  setup-playbook: playbooks/telegram-setup.yaml
                  health:
                    endpoint: "{api-url}/bot{bot-token}/getMe"
                    interval: "30s"
                """;
        var manifest = MAPPER.readValue(yaml, ConnectorManifest.class);

        assertThat(manifest.connector().id()).isEqualTo("telegram");
        assertThat(manifest.connector().setupPlaybook()).isEqualTo("playbooks/telegram-setup.yaml");
        assertThat(manifest.connector().health().endpoint()).contains("{api-url}");
        assertThat(manifest.connector().health().interval()).isEqualTo("30s");

        var apiUrl = manifest.connector().auth().fields().get(1);
        assertThat(apiUrl.isRequired()).isFalse();
        assertThat(apiUrl.isSensitive()).isFalse();
        assertThat(apiUrl.defaultValue()).isEqualTo("https://api.telegram.org");

        var clientBean = manifest.connector().produces().getFirst();
        assertThat(clientBean.constructorArgs()).hasSize(1);
        assertThat(clientBean.constructorArgs().getFirst().field()).isEqualTo("api-url");

        var platformBean = manifest.connector().produces().get(1);
        assertThat(platformBean.constructorArgs()).hasSize(2);
        assertThat(platformBean.constructorArgs().getFirst().bean()).isEqualTo("com.example.BotClient");
        assertThat(platformBean.constructorArgs().get(1).field()).isEqualTo("bot-token");
    }

    @Test
    void field_declaration_defaults_required_and_sensitive_to_true() {
        var field = new ConnectorManifest.FieldDeclaration("test-key", "A test key");
        assertThat(field.isRequired()).isTrue();
        assertThat(field.isSensitive()).isTrue();
        assertThat(field.defaultValue()).isNull();
    }

    @Test
    void field_declaration_rejects_blank_name() {
        assertThatThrownBy(() -> new ConnectorManifest.FieldDeclaration("", "desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field name required");
    }

    @Test
    void constructor_arg_rejects_both_field_and_bean() {
        assertThatThrownBy(() -> new ConnectorManifest.ConstructorArg("api-key", "com.example.Bean"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void constructor_arg_rejects_neither_field_nor_bean() {
        assertThatThrownBy(() -> new ConnectorManifest.ConstructorArg(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void ignores_unknown_yaml_keys_for_forward_compatibility() throws Exception {
        var yaml = """
                connector:
                  id: future
                  platform: chat
                  description: "Future connector"
                  auth:
                    type: oauth
                    token-url: "https://oauth.example.com/token"
                    auth-url: "https://oauth.example.com/authorize"
                    fields:
                      - name: client-id
                        description: "OAuth client ID"
                  produces:
                    - class: com.example.FuturePlatform
                      type: platform
                """;
        var manifest = MAPPER.readValue(yaml, ConnectorManifest.class);
        assertThat(manifest.connector().id()).isEqualTo("future");
        assertThat(manifest.connector().auth().type()).isEqualTo("oauth");
    }
}
