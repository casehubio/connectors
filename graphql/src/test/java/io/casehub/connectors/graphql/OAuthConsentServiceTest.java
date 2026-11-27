package io.casehub.connectors.graphql;

import io.casehub.connectors.lifecycle.ConnectorManifest;
import io.casehub.connectors.lifecycle.ConnectorManifest.AuthDeclaration;
import io.casehub.connectors.lifecycle.ConnectorManifest.ConnectorDeclaration;
import io.casehub.connectors.lifecycle.ConnectorRegistry;
import io.casehub.connectors.lifecycle.ConnectorStatus;
import io.casehub.connectors.lifecycle.FamilyManifest;
import io.casehub.connectors.lifecycle.OAuthConfigRegistry;
import io.casehub.connectors.lifecycle.ProviderCredentialFacade;
import io.casehub.platform.api.authn.StaticCredentialRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthConsentServiceTest {

    private OAuthConfigRegistry oauthConfigs;
    private StoreBackedFacade credentialFacade;
    private CredentialFormServiceTest.RecordingCredentialStore credentialStore;
    private ConnectorRegistry registry;
    private TestableOAuthConsentService service;

    @BeforeEach
    void setUp() {
        var googleOauth = new FamilyManifest.OAuthDeclaration(
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.com/token",
                "{callback-base-url}/oauth/callback/google",
                List.of("https://www.googleapis.com/auth/calendar.readonly",
                        "https://www.googleapis.com/auth/drive.readonly"),
                "offline", "consent");
        var microsoftOauth = new FamilyManifest.OAuthDeclaration(
                "https://login.microsoftonline.com/{tenant-id}/oauth2/v2/authorize",
                "https://login.microsoftonline.com/{tenant-id}/oauth2/v2/token",
                "{callback-base-url}/oauth/callback/microsoft",
                List.of("Calendars.ReadWrite", "Mail.Read"),
                null, null);
        oauthConfigs = new OAuthConfigRegistry(Map.of(
                "google", googleOauth,
                "microsoft", microsoftOauth));

        credentialStore = new CredentialFormServiceTest.RecordingCredentialStore();
        credentialFacade = new StoreBackedFacade(credentialStore, Map.of(
                "google", Map.of("client-id", "google-client-id", "client-secret", "google-client-secret"),
                "microsoft", Map.of("client-id", "ms-client-id", "client-secret", "ms-client-secret",
                        "tenant-id", "my-tenant-123")));
        registry = new ConnectorRegistry(credentialFacade);

        service = new TestableOAuthConsentService(
                oauthConfigs, credentialFacade, credentialStore, registry,
                "http://localhost:8080");
    }

    // --- authorization URL ---

    @Test
    void buildAuthorizationUrl_includes_client_id_and_scopes() {
        var request = service.buildAuthorizationUrl("google");

        var params = parseQueryParams(request.url());
        assertThat(params.get("client_id")).isEqualTo("google-client-id");
        assertThat(params.get("scope")).contains("calendar.readonly");
        assertThat(params.get("scope")).contains("drive.readonly");
        assertThat(params.get("response_type")).isEqualTo("code");
    }

    @Test
    void buildAuthorizationUrl_includes_pkce_challenge() {
        var request = service.buildAuthorizationUrl("google");

        var params = parseQueryParams(request.url());
        assertThat(params.get("code_challenge")).isNotBlank();
        assertThat(params.get("code_challenge_method")).isEqualTo("S256");
    }

    @Test
    void buildAuthorizationUrl_resolves_callback_base_url() {
        var request = service.buildAuthorizationUrl("google");

        var params = parseQueryParams(request.url());
        assertThat(params.get("redirect_uri"))
                .isEqualTo("http://localhost:8080/oauth/callback/google");
    }

    @Test
    void buildAuthorizationUrl_includes_access_type_and_prompt_when_declared() {
        var request = service.buildAuthorizationUrl("google");

        var params = parseQueryParams(request.url());
        assertThat(params.get("access_type")).isEqualTo("offline");
        assertThat(params.get("prompt")).isEqualTo("consent");
    }

    @Test
    void buildAuthorizationUrl_omits_access_type_and_prompt_when_null() {
        var request = service.buildAuthorizationUrl("microsoft");

        var params = parseQueryParams(request.url());
        assertThat(params).doesNotContainKey("access_type");
        assertThat(params).doesNotContainKey("prompt");
    }

    @Test
    void buildAuthorizationUrl_state_is_unique() {
        var request1 = service.buildAuthorizationUrl("google");
        var request2 = service.buildAuthorizationUrl("google");

        assertThat(request1.state()).isNotEqualTo(request2.state());
    }

    @Test
    void buildAuthorizationUrl_resolves_template_variables_in_auth_url() {
        var request = service.buildAuthorizationUrl("microsoft");

        assertThat(request.url())
                .startsWith("https://login.microsoftonline.com/my-tenant-123/oauth2/v2/authorize?");
    }

    @Test
    void buildAuthorizationUrl_throws_for_unknown_credential_key() {
        assertThatThrownBy(() -> service.buildAuthorizationUrl("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void buildAuthorizationUrl_throws_when_client_id_not_configured() {
        var emptyFacade = new StoreBackedFacade(credentialStore, Map.of());
        var svc = new TestableOAuthConsentService(
                oauthConfigs, emptyFacade, credentialStore, registry,
                "http://localhost:8080");

        assertThatThrownBy(() -> svc.buildAuthorizationUrl("google"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client-id");
    }

    // --- code exchange ---

    @Test
    void exchangeCode_unknown_state_throws() {
        assertThatThrownBy(() -> service.exchangeCode("auth-code", "bogus-state"))
                .isInstanceOf(OAuthConsentService.InvalidOAuthStateException.class)
                .hasMessageContaining("Unknown or expired");
    }

    @Test
    void exchangeCode_stores_access_and_refresh_tokens() {
        service.setTokenResponse("""
                {"access_token":"ya29.test-access","refresh_token":"1//test-refresh","expires_in":3600}""");

        var request = service.buildAuthorizationUrl("google");
        var result = service.exchangeCode("auth-code-123", request.state());

        assertThat(result.credentialKey()).isEqualTo("google");
        assertThat(result.success()).isTrue();
        assertThat(credentialStore.stored).anySatisfy(r ->
                assertThat(r.provider()).isEqualTo("google.access-token"));
        assertThat(credentialStore.stored).anySatisfy(r ->
                assertThat(r.provider()).isEqualTo("google.refresh-token"));
    }

    @Test
    void exchangeCode_sends_code_verifier_to_token_endpoint() {
        service.setTokenResponse("""
                {"access_token":"ya29.test","refresh_token":"1//test","expires_in":3600}""");

        var request = service.buildAuthorizationUrl("google");
        service.exchangeCode("auth-code", request.state());

        assertThat(service.lastTokenRequestBody()).contains("code_verifier=");
        assertThat(service.lastTokenRequestBody()).contains("grant_type=authorization_code");
    }

    @Test
    void exchangeCode_resolves_template_variables_in_token_url() {
        service.setTokenResponse("""
                {"access_token":"test","refresh_token":"test","expires_in":3600}""");

        var request = service.buildAuthorizationUrl("microsoft");
        service.exchangeCode("auth-code", request.state());

        assertThat(service.lastTokenUrl())
                .isEqualTo("https://login.microsoftonline.com/my-tenant-123/oauth2/v2/token");
    }

    @Test
    void exchangeCode_updates_registry_status_for_family() {
        service.setTokenResponse("""
                {"access_token":"ya29.test","refresh_token":"1//test","expires_in":3600}""");

        var googleManifest = new ConnectorManifest(new ConnectorDeclaration(
                "calendar-google", "calendar", "Google Calendar",
                new AuthDeclaration("oauth2", "google", null,
                        List.of(new ConnectorManifest.FieldDeclaration("access-token", "Access token"))),
                List.of(), List.of(), List.of(), null, null));
        registry.register(googleManifest, ConnectorStatus.UNCONFIGURED);

        var request = service.buildAuthorizationUrl("google");
        service.exchangeCode("auth-code", request.state());

        assertThat(registry.status("calendar-google")).isEqualTo(ConnectorStatus.READY);
    }

    @Test
    void exchangeCode_state_is_single_use() {
        service.setTokenResponse("""
                {"access_token":"ya29.test","refresh_token":"1//test","expires_in":3600}""");

        var request = service.buildAuthorizationUrl("google");
        service.exchangeCode("auth-code", request.state());

        assertThatThrownBy(() -> service.exchangeCode("auth-code", request.state()))
                .isInstanceOf(OAuthConsentService.InvalidOAuthStateException.class);
    }

    // --- helpers ---

    private static Map<String, String> parseQueryParams(String url) {
        var result = new LinkedHashMap<String, String>();
        var queryStart = url.indexOf('?');
        if (queryStart < 0) return result;
        var query = url.substring(queryStart + 1);
        for (var pair : query.split("&")) {
            var parts = pair.split("=", 2);
            var key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            var value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            result.put(key, value);
        }
        return result;
    }

    static class StoreBackedFacade implements ProviderCredentialFacade {
        private final CredentialFormServiceTest.RecordingCredentialStore store;
        private final Map<String, Map<String, String>> staticCredentials;

        StoreBackedFacade(CredentialFormServiceTest.RecordingCredentialStore store,
                          Map<String, Map<String, String>> staticCredentials) {
            this.store = store;
            this.staticCredentials = staticCredentials;
        }

        @Override
        public Optional<String> resolve(String providerId, String field) {
            var staticVal = Optional.ofNullable(
                    staticCredentials.getOrDefault(providerId, Map.of()).get(field));
            if (staticVal.isPresent()) return staticVal;
            return store.find("system", providerId + "." + field, "default")
                    .map(StaticCredentialRecord::credential);
        }

        @Override
        public Map<String, String> resolveAll(String providerId) {
            return staticCredentials.getOrDefault(providerId, Map.of());
        }

        @Override
        public boolean isConfigured(String providerId, List<String> requiredFields) {
            return requiredFields.stream().allMatch(f -> resolve(providerId, f).isPresent());
        }

        @Override
        public CredentialSource source(String providerId, String field) {
            return resolve(providerId, field).isPresent() ? CredentialSource.CREDENTIAL_STORE : CredentialSource.NONE;
        }
    }

    static class TestableOAuthConsentService extends OAuthConsentService {

        private String tokenResponse;
        private String lastTokenRequestBody;
        private String lastTokenUrl;

        TestableOAuthConsentService(OAuthConfigRegistry oauthConfigs,
                                    ProviderCredentialFacade credentialFacade,
                                    CredentialFormServiceTest.RecordingCredentialStore credentialStore,
                                    ConnectorRegistry registry, String callbackBaseUrl) {
            super(oauthConfigs, credentialFacade, credentialStore, registry, callbackBaseUrl);
        }

        void setTokenResponse(String json) {
            this.tokenResponse = json;
        }

        String lastTokenRequestBody() {
            return lastTokenRequestBody;
        }

        String lastTokenUrl() {
            return lastTokenUrl;
        }

        @Override
        String postTokenRequest(String tokenUrl, String body) throws IOException {
            this.lastTokenUrl = tokenUrl;
            this.lastTokenRequestBody = body;
            if (tokenResponse == null) {
                throw new IOException("No token response configured in test");
            }
            return tokenResponse;
        }
    }
}
