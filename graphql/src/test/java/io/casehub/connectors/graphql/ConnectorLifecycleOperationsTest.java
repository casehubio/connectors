package io.casehub.connectors.graphql;

import io.casehub.connectors.graphql.dto.ConnectorDiscoverResult;
import io.casehub.connectors.graphql.dto.ConnectorHealthResult;
import io.casehub.connectors.lifecycle.ConnectorManifest;
import io.casehub.connectors.lifecycle.ConnectorManifest.AuthDeclaration;
import io.casehub.connectors.lifecycle.ConnectorManifest.BeanDeclaration;
import io.casehub.connectors.lifecycle.ConnectorManifest.ConnectorDeclaration;
import io.casehub.connectors.lifecycle.ConnectorManifest.FieldDeclaration;
import io.casehub.connectors.lifecycle.ConnectorManifest.HealthDeclaration;
import io.casehub.connectors.lifecycle.ConnectorRegistry;
import io.casehub.connectors.lifecycle.ConnectorStatus;
import io.casehub.connectors.lifecycle.InMemoryCredentialFacade;
import io.casehub.connectors.lifecycle.ProviderCredentialFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;



class ConnectorLifecycleOperationsTest {

    private ConnectorRegistry registry;
    private ProviderCredentialFacade credentialFacade;
    private ConnectorLifecycleOperations operations;

    private static ConnectorManifest numbrrsManifest() {
        return new ConnectorManifest(new ConnectorDeclaration(
                "numbrrs", "mobile",
                "Numbrrs — UK mobile SIM numbers",
                new AuthDeclaration("api-key", null, List.of(
                        new FieldDeclaration("api-key", true, "API key", true, null, null, null),
                        new FieldDeclaration("signing-secret", false, "Signing secret", true, null, null, null),
                        new FieldDeclaration("numbers", false, "Phone numbers", false, null, null, null))),
                List.of(new BeanDeclaration("io.casehub.connectors.mobile.numbrrs.NumbrrsMobilePlatform",
                        null, "platform", null)),
                List.of(), List.of(), null, null));
    }

    private static ConnectorManifest telegramManifest() {
        return new ConnectorManifest(new ConnectorDeclaration(
                "telegram", "chat",
                "Telegram Bot API",
                new AuthDeclaration("bot-token", null, List.of(
                        new FieldDeclaration("bot-token", true, "Bot token", true, null, null, null),
                        new FieldDeclaration("api-url", false, "API base URL", false,
                                "https://api.telegram.org", null, null),
                        new FieldDeclaration("webhook-secret", false, "Webhook secret", true, null, null, null))),
                List.of(new BeanDeclaration("io.casehub.connectors.chat.telegram.TelegramChatPlatform",
                        null, "platform", null)),
                List.of(), List.of(),
                "playbooks/telegram-webhook-setup.yaml",
                new HealthDeclaration("{api-url}/bot{bot-token}/getMe", "30s")));
    }

    private static ConnectorManifest signalManifest() {
        return new ConnectorManifest(new ConnectorDeclaration(
                "signal", "chat",
                "Signal via signal-cli-rest-api",
                new AuthDeclaration("phone-verify", null, List.of(
                        new FieldDeclaration("api-url", true, "signal-cli API URL", false, null, null, null),
                        new FieldDeclaration("number", true, "Phone number", false, null, null, null))),
                List.of(new BeanDeclaration("io.casehub.connectors.chat.signal.SignalChatPlatform",
                        null, "platform", null)),
                List.of(), List.of(),
                "playbooks/signal-registration.yaml", null));
    }

    @BeforeEach
    void setUp() {
        registry = new ConnectorRegistry();
        credentialFacade = new InMemoryCredentialFacade(Map.of(
                "numbrrs", Map.of("api-key", "test-key-123"),
                "telegram", Map.of("bot-token", "123:ABC", "webhook-secret", "secret")));

        registry.register(numbrrsManifest(), ConnectorStatus.READY);
        registry.register(telegramManifest(), ConnectorStatus.READY);
        registry.register(signalManifest(), ConnectorStatus.UNCONFIGURED);

        operations = new ConnectorLifecycleOperations(registry, credentialFacade);
    }

    // --- connector_discover ---

    @Test
    void discover_returns_all_connectors() {
        var result = operations.connectorDiscover(null);

        assertThat(result.connectors()).hasSize(3);
        assertThat(result.connectors()).extracting(ConnectorDiscoverResult.DiscoveredConnector::id)
                .containsExactlyInAnyOrder("numbrrs", "telegram", "signal");
    }

    @Test
    void discover_filters_by_platform() {
        var result = operations.connectorDiscover("chat");

        assertThat(result.connectors()).hasSize(2);
        assertThat(result.connectors()).extracting(ConnectorDiscoverResult.DiscoveredConnector::id)
                .containsExactlyInAnyOrder("telegram", "signal");
    }

    @Test
    void discover_includes_field_classification() {
        var result = operations.connectorDiscover(null);
        var numbrrs = result.connectors().stream()
                .filter(c -> c.id().equals("numbrrs")).findFirst().orElseThrow();

        assertThat(numbrrs.requiredFields()).containsExactly("api-key");
        assertThat(numbrrs.optionalFields()).containsExactlyInAnyOrder("signing-secret", "numbers");
    }

    @Test
    void discover_reports_missing_fields_for_unconfigured() {
        var result = operations.connectorDiscover(null);
        var signal = result.connectors().stream()
                .filter(c -> c.id().equals("signal")).findFirst().orElseThrow();

        assertThat(signal.status()).isEqualTo(ConnectorStatus.UNCONFIGURED);
        assertThat(signal.missingFields()).containsExactlyInAnyOrder("api-url", "number");
    }

    @Test
    void discover_reports_status_and_auth_type() {
        var result = operations.connectorDiscover(null);
        var telegram = result.connectors().stream()
                .filter(c -> c.id().equals("telegram")).findFirst().orElseThrow();

        assertThat(telegram.status()).isEqualTo(ConnectorStatus.READY);
        assertThat(telegram.authType()).isEqualTo("bot-token");
        assertThat(telegram.setupPlaybook()).isEqualTo("playbooks/telegram-webhook-setup.yaml");
    }

    @Test
    void discover_returns_empty_for_unknown_platform() {
        var result = operations.connectorDiscover("bank");

        assertThat(result.connectors()).isEmpty();
    }

    // --- connector_status ---

    @Test
    void status_returns_detailed_credential_info() {
        var results = operations.connectorStatus("telegram");

        assertThat(results).hasSize(1);
        var status = results.getFirst();
        assertThat(status.id()).isEqualTo("telegram");
        assertThat(status.status()).isEqualTo(ConnectorStatus.READY);

        assertThat(status.credentials()).containsKeys("bot-token", "api-url", "webhook-secret");

        var botToken = status.credentials().get("bot-token");
        assertThat(botToken.configured()).isTrue();
        assertThat(botToken.source()).isEqualTo("CREDENTIAL_STORE");

        var apiUrl = status.credentials().get("api-url");
        assertThat(apiUrl.configured()).isFalse();
        assertThat(apiUrl.defaultValue()).isEqualTo("https://api.telegram.org");
    }

    @Test
    void status_returns_all_when_no_id() {
        var results = operations.connectorStatus(null);

        assertThat(results).hasSize(3);
    }

    @Test
    void status_throws_for_unknown_connector() {
        assertThatThrownBy(() -> operations.connectorStatus("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void status_includes_setup_playbook_and_health() {
        var results = operations.connectorStatus("telegram");
        var status = results.getFirst();

        assertThat(status.setupPlaybook()).isEqualTo("playbooks/telegram-webhook-setup.yaml");
        assertThat(status.healthEndpoint()).isEqualTo("{api-url}/bot{bot-token}/getMe");
    }

    // --- connector_setup ---

    @Test
    void setup_validates_configured_connector() {
        var result = operations.connectorSetup("numbrrs", false);

        assertThat(result.connectorId()).isEqualTo("numbrrs");
        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("configured");
    }

    @Test
    void setup_reports_missing_credentials() {
        var result = operations.connectorSetup("signal", false);

        assertThat(result.success()).isFalse();
        assertThat(result.missingFields()).containsExactlyInAnyOrder("api-url", "number");
    }

    @Test
    void setup_dry_run_does_not_modify_state() {
        var result = operations.connectorSetup("signal", true);

        assertThat(result.success()).isFalse();
        assertThat(result.dryRun()).isTrue();
        assertThat(result.missingFields()).isNotEmpty();
    }

    @Test
    void setup_throws_for_unknown_connector() {
        assertThatThrownBy(() -> operations.connectorSetup("unknown", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- connector_health ---

    @Test
    void health_returns_configured_connectors() {
        var result = operations.connectorHealth();

        assertThat(result.connectors()).extracting(ConnectorHealthResult.ConnectorHealthEntry::id)
                .containsExactlyInAnyOrder("numbrrs", "telegram");
        assertThat(result.connectors()).allSatisfy(e ->
                assertThat(e.status()).isEqualTo(ConnectorStatus.READY));
    }

    @Test
    void health_excludes_unconfigured_connectors() {
        var result = operations.connectorHealth();

        assertThat(result.connectors()).extracting(ConnectorHealthResult.ConnectorHealthEntry::id)
                .doesNotContain("signal");
    }

    @Test
    void health_includes_health_endpoint_when_declared() {
        var result = operations.connectorHealth();
        var telegram = result.connectors().stream()
                .filter(e -> e.id().equals("telegram")).findFirst().orElseThrow();

        assertThat(telegram.healthEndpoint()).isEqualTo("{api-url}/bot{bot-token}/getMe");
    }

    @Test
    void health_reports_null_endpoint_when_not_declared() {
        var result = operations.connectorHealth();
        var numbrrs = result.connectors().stream()
                .filter(e -> e.id().equals("numbrrs")).findFirst().orElseThrow();

        assertThat(numbrrs.healthEndpoint()).isNull();
    }

// --- credential_input ---

    @Test
    void credentialInput_returns_form_url_with_token() {
        var store       = new CredentialFormServiceTest.RecordingCredentialStore();
        var formService = new CredentialFormService(store);
        var ops         = new ConnectorLifecycleOperations(registry, credentialFacade, formService);

        var result = ops.credentialInput("numbrrs");

        assertThat(result.connectorId()).isEqualTo("numbrrs");
        assertThat(result.formUrl()).startsWith("/api/connectors/lifecycle/credential-form?token=");
        assertThat(result.message()).contains("never appear");
    }

    @Test
    void credentialInput_throws_for_unknown_connector() {
        var store       = new CredentialFormServiceTest.RecordingCredentialStore();
        var formService = new CredentialFormService(store);
        var ops         = new ConnectorLifecycleOperations(registry, credentialFacade, formService);

        assertThatThrownBy(() -> ops.credentialInput("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void credentialInput_returns_error_when_form_service_unavailable() {
        var ops = new ConnectorLifecycleOperations(registry, credentialFacade);

        var result = ops.credentialInput("numbrrs");

        assertThat(result.connectorId()).isEqualTo("numbrrs");
        assertThat(result.formUrl()).isNull();
        assertThat(result.message()).contains("not available");
    }
}
