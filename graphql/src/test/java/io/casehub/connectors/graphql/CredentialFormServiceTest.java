package io.casehub.connectors.graphql;

import io.casehub.connectors.lifecycle.ConnectorManifest;
import io.casehub.connectors.lifecycle.ConnectorManifest.AuthDeclaration;
import io.casehub.connectors.lifecycle.ConnectorManifest.ConnectorDeclaration;
import io.casehub.connectors.lifecycle.ConnectorManifest.FieldDeclaration;
import io.casehub.platform.api.authn.CredentialType;
import io.casehub.platform.api.authn.StaticCredentialRecord;
import io.casehub.platform.api.authn.StaticCredentialStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialFormServiceTest {

    private RecordingCredentialStore credentialStore;
    private CredentialFormService service;

    @BeforeEach
    void setUp() {
        credentialStore = new RecordingCredentialStore();
        service = new CredentialFormService(credentialStore);
    }

    // --- session lifecycle ---

    @Test
    void createSession_returns_unique_tokens() {
        var token1 = service.createSession("numbrrs", numbrrsManifest());
        var token2 = service.createSession("numbrrs", numbrrsManifest());

        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void session_returns_empty_for_unknown_token() {
        assertThat(service.session("nonexistent")).isEmpty();
    }

    @Test
    void session_returns_session_for_valid_token() {
        var token = service.createSession("numbrrs", numbrrsManifest());

        var session = service.session(token);
        assertThat(session).isPresent();
        assertThat(session.get().connectorId()).isEqualTo("numbrrs");
        assertThat(session.get().consumed()).isFalse();
    }

    // --- form HTML generation ---

    @Test
    void generateFormHtml_includes_connector_name_and_description() {
        var token = service.createSession("numbrrs", numbrrsManifest());

        var html = service.generateFormHtml(token);

        assertThat(html).contains("numbrrs");
        assertThat(html).contains("Numbrrs — UK mobile SIM numbers");
    }

    @Test
    void generateFormHtml_renders_fields_with_labels() {
        var token = service.createSession("numbrrs", numbrrsManifest());

        var html = service.generateFormHtml(token);

        assertThat(html).contains("Numbrrs API key");
        assertThat(html).contains("Signing secret");
        assertThat(html).contains("Phone numbers");
    }

    @Test
    void generateFormHtml_uses_password_for_sensitive_and_text_for_nonsensitive() {
        var token = service.createSession("numbrrs", numbrrsManifest());

        var html = service.generateFormHtml(token);

        assertThat(html).contains("type=\"password\" name=\"api-key\"");
        assertThat(html).contains("type=\"password\" name=\"signing-secret\"");
        assertThat(html).contains("type=\"text\" name=\"numbers\"");
    }

    @Test
    void generateFormHtml_includes_help_text() {
        var token = service.createSession("numbrrs", numbrrsManifest());

        var html = service.generateFormHtml(token);

        assertThat(html).contains("Log in to your Numbrrs dashboard");
    }

    @Test
    void generateFormHtml_includes_example_as_placeholder() {
        var token = service.createSession("numbrrs", numbrrsManifest());

        var html = service.generateFormHtml(token);

        assertThat(html).contains("placeholder=\"nmbr_sk_1a2b3c4d5e6f\"");
    }

    @Test
    void generateFormHtml_marks_optional_fields() {
        var token = service.createSession("numbrrs", numbrrsManifest());

        var html = service.generateFormHtml(token);

        assertThat(html).contains("(optional)");
    }

    @Test
    void generateFormHtml_sets_required_on_required_fields() {
        var token = service.createSession("numbrrs", numbrrsManifest());

        var html = service.generateFormHtml(token);

        assertThat(html).contains("name=\"api-key\"");
        assertThat(html).matches("(?s).*name=\"api-key\"[^>]*required.*");
    }

    @Test
    void generateFormHtml_includes_default_value() {
        var manifest = new ConnectorManifest(new ConnectorDeclaration(
                "test", "chat", "Test connector",
                new AuthDeclaration("api-key", null, null, null, List.of(
                        new FieldDeclaration("url", true, "API URL", false,
                                "https://api.example.com", null, null))),
                List.of(), List.of(), List.of(), null, null));
        var token = service.createSession("test", manifest);

        var html = service.generateFormHtml(token);

        assertThat(html).contains("value=\"https://api.example.com\"");
    }

    @Test
    void generateFormHtml_returns_error_for_unknown_token() {
        var html = service.generateFormHtml("bad-token");

        assertThat(html).contains("Invalid or expired");
    }

    @Test
    void generateFormHtml_returns_error_for_consumed_session() {
        var token = service.createSession("numbrrs", numbrrsManifest());
        service.submitCredentials(token, Map.of("api-key", "real-key"));

        var html = service.generateFormHtml(token);

        assertThat(html).contains("already been submitted");
    }

    @Test
    void generateFormHtml_escapes_xss_in_field_values() {
        var manifest = new ConnectorManifest(new ConnectorDeclaration(
                "xss", "chat", "<script>alert(1)</script>",
                new AuthDeclaration("api-key", null, null, null, List.of(
                        new FieldDeclaration("key", true, "Key <b>bold</b>", true,
                                null, "Help <script>", "ex<ample"))),
                List.of(), List.of(), List.of(), null, null));
        var token = service.createSession("xss", manifest);

        var html = service.generateFormHtml(token);

        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    // --- credential submission ---

    @Test
    void submitCredentials_stores_values_in_credential_store() {
        var token = service.createSession("numbrrs", numbrrsManifest());

        var stored = service.submitCredentials(token, Map.of(
                "api-key", "real-key-123",
                "signing-secret", "real-secret"));

        assertThat(stored).isTrue();
        assertThat(credentialStore.stored).hasSize(2);
        assertThat(credentialStore.stored).anySatisfy(r -> {
            assertThat(r.provider()).isEqualTo("numbrrs.api-key");
            assertThat(r.credential()).isEqualTo("real-key-123");
            assertThat(r.type()).isEqualTo(CredentialType.API_KEY);
        });
        assertThat(credentialStore.stored).anySatisfy(r -> {
            assertThat(r.provider()).isEqualTo("numbrrs.signing-secret");
            assertThat(r.credential()).isEqualTo("real-secret");
        });
    }

    @Test
    void submitCredentials_skips_blank_values() {
        var token = service.createSession("numbrrs", numbrrsManifest());

        service.submitCredentials(token, Map.of("api-key", "real-key", "numbers", ""));

        assertThat(credentialStore.stored).hasSize(1);
    }

    @Test
    void submitCredentials_marks_session_consumed() {
        var token = service.createSession("numbrrs", numbrrsManifest());

        service.submitCredentials(token, Map.of("api-key", "real-key"));

        assertThat(service.session(token).get().consumed()).isTrue();
    }

    @Test
    void submitCredentials_rejects_replay() {
        var token = service.createSession("numbrrs", numbrrsManifest());
        service.submitCredentials(token, Map.of("api-key", "real-key"));

        var replay = service.submitCredentials(token, Map.of("api-key", "different-key"));

        assertThat(replay).isFalse();
        assertThat(credentialStore.stored).hasSize(1);
    }

    @Test
    void submitCredentials_rejects_unknown_token() {
        var result = service.submitCredentials("bad-token", Map.of("key", "value"));

        assertThat(result).isFalse();
        assertThat(credentialStore.stored).isEmpty();
    }

    @Test
    void submitCredentials_uses_bot_token_type_for_bot_token_auth() {
        var manifest = new ConnectorManifest(new ConnectorDeclaration(
                "telegram", "chat", "Telegram",
                new AuthDeclaration("bot-token", null, null, null, List.of(
                        new FieldDeclaration("bot-token", true, "Bot token", true, null, null, null))),
                List.of(), List.of(), List.of(), null, null));
        var token = service.createSession("telegram", manifest);

        service.submitCredentials(token, Map.of("bot-token", "123:ABC"));

        assertThat(credentialStore.stored.getFirst().type()).isEqualTo(CredentialType.BOT_TOKEN);
    }

    // --- registry status update ---

    @Test
    void submitCredentials_updates_registry_status() {
        var facade = new OAuthConsentServiceTest.StoreBackedFacade(credentialStore, Map.of());
        var registry = new io.casehub.connectors.lifecycle.ConnectorRegistry(facade);
        var svc = new CredentialFormService(credentialStore, facade, registry);

        registry.register(numbrrsManifest(), io.casehub.connectors.lifecycle.ConnectorStatus.UNCONFIGURED);

        var token = svc.createSession("numbrrs", numbrrsManifest());
        svc.submitCredentials(token, Map.of("api-key", "real-key"));

        assertThat(registry.status("numbrrs"))
                .isEqualTo(io.casehub.connectors.lifecycle.ConnectorStatus.READY);
    }

    // --- helpers ---

    private static ConnectorManifest numbrrsManifest() {
        return new ConnectorManifest(new ConnectorDeclaration(
                "numbrrs", "mobile",
                "Numbrrs — UK mobile SIM numbers",
                new AuthDeclaration("api-key", null, null, null, List.of(
                        new FieldDeclaration("api-key", true, "Numbrrs API key", true, null,
                                "Log in to your Numbrrs dashboard at Settings → API Keys",
                                "nmbr_sk_1a2b3c4d5e6f"),
                        new FieldDeclaration("signing-secret", false, "Signing secret", true, null,
                                "Found alongside the API key", "whsec_abcdef123456"),
                        new FieldDeclaration("numbers", false, "Phone numbers", false, null,
                                "Your allocated UK mobile numbers", "+447700900001,+447700900002"))),
                List.of(), List.of(), List.of(), null, null));
    }

    static class RecordingCredentialStore implements StaticCredentialStore {
        final List<StaticCredentialRecord> stored = new ArrayList<>();

        @Override
        public void store(StaticCredentialRecord record) {
            stored.add(record);
        }

        @Override
        public Optional<StaticCredentialRecord> find(String actorId, String provider, String tenancyId) {
            return stored.stream()
                    .filter(r -> r.actorId().equals(actorId) && r.provider().equals(provider)
                                 && r.tenancyId().equals(tenancyId))
                    .findFirst();
        }

        @Override
        public List<StaticCredentialRecord> findAll(String actorId, String tenancyId) {
            return stored.stream()
                    .filter(r -> r.actorId().equals(actorId) && r.tenancyId().equals(tenancyId))
                    .toList();
        }

        @Override
        public void delete(String actorId, String provider, String tenancyId) {
            stored.removeIf(r -> r.actorId().equals(actorId) && r.provider().equals(provider)
                                 && r.tenancyId().equals(tenancyId));
        }
    }
}
