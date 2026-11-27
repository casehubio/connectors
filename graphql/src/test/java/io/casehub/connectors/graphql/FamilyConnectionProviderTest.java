package io.casehub.connectors.graphql;

import io.casehub.connectors.lifecycle.FamilyManifest;
import io.casehub.connectors.lifecycle.ProviderCredentialFacade;
import io.casehub.platform.api.authn.ServiceConnectionException;
import io.casehub.platform.api.authn.ServiceConnectionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FamilyConnectionProviderTest {

    @Test
    void getAccessToken_returns_token_from_facade() {
        var facade = mutableFacade(Map.of(
                "google", Map.of(
                        "access-token", "test-access-token",
                        "refresh-token", "test-refresh-token",
                        "expires-at", Instant.now().plusSeconds(3600).toString(),
                        "granted-scopes", "calendar.readonly gmail.readonly")));

        var provider = new FamilyConnectionProvider(facade, Map.of("google", googleFamily()));

        var token = provider.getAccessToken("user1", "google", "default");

        assertThat(token.accessToken()).isEqualTo("test-access-token");
        assertThat(token.expiresAt()).isAfter(Instant.now());
        assertThat(token.grantedScopes()).contains("calendar.readonly", "gmail.readonly");
    }

    @Test
    void getAccessToken_throws_for_non_family_provider() {
        var provider = new FamilyConnectionProvider(
                mutableFacade(Map.of()), Map.of("google", googleFamily()));

        assertThatThrownBy(() -> provider.getAccessToken("user1", "github", "default"))
                .isInstanceOf(ServiceConnectionException.class)
                .hasMessageContaining("No family");
    }

    @Test
    void getAccessToken_throws_when_no_access_token() {
        var facade = mutableFacade(Map.of("google", Map.of("client-id", "id")));
        var provider = new FamilyConnectionProvider(facade, Map.of("google", googleFamily()));

        assertThatThrownBy(() -> provider.getAccessToken("user1", "google", "default"))
                .isInstanceOf(ServiceConnectionException.class)
                .hasMessageContaining("No access token");
    }

    @Test
    void getAccessToken_refreshes_expired_token() {
        var facade = mutableFacade(Map.of(
                "google", Map.of(
                        "access-token", "expired-token",
                        "refresh-token", "test-refresh",
                        "client-id", "test-client-id",
                        "client-secret", "test-secret",
                        "expires-at", Instant.now().minusSeconds(60).toString(),
                        "granted-scopes", "calendar.readonly")));

        var provider = new FamilyConnectionProvider(facade, Map.of("google", googleFamily())) {
            @Override
            String postTokenRequest(String tokenUrl, String body) {
                return """
                        {"access_token":"refreshed-token","expires_in":3600}""";
            }
        };

        var token = provider.getAccessToken("user1", "google", "default");
        assertThat(token.accessToken()).isEqualTo("refreshed-token");
        assertThat(token.expiresAt()).isAfter(Instant.now());
        assertThat(facade.resolve("google", "access-token")).hasValue("refreshed-token");
    }

    @Test
    void getAccessToken_stores_rotated_refresh_token() {
        var facade = mutableFacade(Map.of(
                "google", Map.of(
                        "access-token", "old",
                        "refresh-token", "old-refresh",
                        "client-id", "cid",
                        "client-secret", "cs",
                        "expires-at", Instant.now().minusSeconds(60).toString())));

        var provider = new FamilyConnectionProvider(facade, Map.of("google", googleFamily())) {
            @Override
            String postTokenRequest(String tokenUrl, String body) {
                return """
                        {"access_token":"new-at","refresh_token":"new-rt","expires_in":3600}""";
            }
        };

        provider.getAccessToken("user1", "google", "default");
        assertThat(facade.resolve("google", "refresh-token")).hasValue("new-rt");
    }

    @Test
    void getConnection_returns_connected_when_token_present() {
        var facade = mutableFacade(Map.of(
                "google", Map.of("access-token", "token", "granted-scopes", "calendar.readonly")));

        var provider = new FamilyConnectionProvider(facade, Map.of("google", googleFamily()));
        var conn = provider.getConnection("user1", "google", "default");

        assertThat(conn.status()).isEqualTo(ServiceConnectionStatus.CONNECTED);
        assertThat(conn.grantedScopes()).contains("calendar.readonly");
    }

    @Test
    void getConnection_returns_disconnected_when_no_token() {
        var facade = mutableFacade(Map.of());
        var provider = new FamilyConnectionProvider(facade, Map.of("google", googleFamily()));
        var conn = provider.getConnection("user1", "google", "default");

        assertThat(conn.status()).isEqualTo(ServiceConnectionStatus.DISCONNECTED);
    }

    @Test
    void listConnections_returns_one_per_family() {
        var facade = mutableFacade(Map.of("google", Map.of("access-token", "t")));
        var provider = new FamilyConnectionProvider(facade,
                Map.of("google", googleFamily(), "microsoft", microsoftFamily()));

        var connections = provider.listConnections("user1", "default");
        assertThat(connections).hasSize(2);
    }

    @Test
    void resolves_microsoft_to_delegated_flow() {
        var facade = mutableFacade(Map.of(
                "microsoft", Map.of(
                        "access-token", "delegated-token",
                        "expires-at", Instant.now().plusSeconds(3600).toString())));
        var provider = new FamilyConnectionProvider(facade,
                                                    Map.of("microsoft", microsoftFamilyWithFlows()));

        var token = provider.getAccessToken(null, "microsoft", "default");
        assertThat(token.accessToken()).isEqualTo("delegated-token");
    }

    @Test
    void resolves_microsoft_app_to_application_flow() {
        var facade = mutableFacade(Map.of(
                "microsoft-app", Map.of(
                        "access-token", "app-token",
                        "expires-at", Instant.now().plusSeconds(3600).toString())));
        var provider = new FamilyConnectionProvider(facade,
                                                    Map.of("microsoft", microsoftFamilyWithFlows()));

        var token = provider.getAccessToken("__application__", "microsoft-app", "default");
        assertThat(token.accessToken()).isEqualTo("app-token");
    }

    @Test
    void resolves_microsoft_bot_to_bot_flow() {
        var facade = mutableFacade(Map.of(
                "microsoft-bot", Map.of(
                        "access-token", "bot-token",
                        "expires-at", Instant.now().plusSeconds(3600).toString())));
        var provider = new FamilyConnectionProvider(facade,
                                                    Map.of("microsoft", microsoftFamilyWithFlows()));

        var token = provider.getAccessToken("__application__", "microsoft-bot", "default");
        assertThat(token.accessToken()).isEqualTo("bot-token");
    }

    @Test
    void unknown_suffixed_provider_throws() {
        var provider = new FamilyConnectionProvider(
                mutableFacade(Map.of()), Map.of("microsoft", microsoftFamilyWithFlows()));

        assertThatThrownBy(() -> provider.getAccessToken(null, "microsoft-unknown", "default"))
                .isInstanceOf(ServiceConnectionException.class)
                .hasMessageContaining("No family");
    }

    @Test
    void acquires_app_token_via_client_credentials_when_missing() {
        var facade = mutableFacade(Map.of(
                "microsoft", Map.of("client-id", "test-cid", "client-secret", "test-cs")));
        var testProvider = new TestableFamilyConnectionProvider(facade,
                                                                Map.of("microsoft", microsoftFamilyWithFlows()));
        testProvider.setTokenResponse("""
                                      {"access_token": "acquired-app-token", "expires_in": 3600, "token_type": "Bearer"}""");

        var token = testProvider.getAccessToken("__application__", "microsoft-app", "default");
        assertThat(token.accessToken()).isEqualTo("acquired-app-token");
        assertThat(testProvider.lastTokenRequestUrl())
                .isEqualTo("https://login.microsoftonline.com/test-tenant/oauth2/v2/token");
        assertThat(testProvider.lastTokenRequestBody())
                .contains("grant_type=client_credentials")
                .contains("scope=");
    }

    @Test
    void acquires_bot_token_using_flow_specific_token_url() {
        var facade = mutableFacade(Map.of(
                "microsoft", Map.of("client-id", "test-cid", "client-secret", "test-cs")));
        var testProvider = new TestableFamilyConnectionProvider(facade,
                                                                Map.of("microsoft", microsoftFamilyWithFlows()));
        testProvider.setTokenResponse("""
                                      {"access_token": "bot-token", "expires_in": 3600, "token_type": "Bearer"}""");

        var token = testProvider.getAccessToken("__application__", "microsoft-bot", "default");
        assertThat(token.accessToken()).isEqualTo("bot-token");
        assertThat(testProvider.lastTokenRequestUrl())
                .isEqualTo("https://login.microsoftonline.com/botframework.com/oauth2/v2.0/token");
    }

    @Test
    void expired_client_credentials_token_reacquires() {
        var facade = mutableFacade(Map.of(
                "microsoft", Map.of("client-id", "test-cid", "client-secret", "test-cs"),
                "microsoft-app", Map.of(
                        "access-token", "old-token",
                        "expires-at", Instant.now().minusSeconds(60).toString())));
        var testProvider = new TestableFamilyConnectionProvider(facade,
                                                                Map.of("microsoft", microsoftFamilyWithFlows()));
        testProvider.setTokenResponse("""
                                      {"access_token": "fresh-token", "expires_in": 3600, "token_type": "Bearer"}""");

        var token = testProvider.getAccessToken("__application__", "microsoft-app", "default");
        assertThat(token.accessToken()).isEqualTo("fresh-token");
    }

    @Test
    void valid_client_credentials_token_returned_without_reacquire() {
        var facade = mutableFacade(Map.of(
                "microsoft-app", Map.of(
                        "access-token", "cached-token",
                        "expires-at", Instant.now().plusSeconds(3600).toString())));
        var testProvider = new TestableFamilyConnectionProvider(facade,
                                                                Map.of("microsoft", microsoftFamilyWithFlows()));

        var token = testProvider.getAccessToken("__application__", "microsoft-app", "default");
        assertThat(token.accessToken()).isEqualTo("cached-token");
        assertThat(testProvider.lastTokenRequestUrl()).isNull();
    }


    private static FamilyManifest googleFamily() {
        var oauth = new FamilyManifest.OAuthDeclaration(
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.com/token",
                "{callback-base-url}/oauth/callback/google",
                List.of("https://www.googleapis.com/auth/calendar.readonly"),
                "offline", "consent");
        return new FamilyManifest(new FamilyManifest.FamilyDeclaration(
                "google", "Google Workspace", FamilyManifest.ConsentStrategy.INCREMENTAL, oauth, null, List.of()));
    }

    private static FamilyManifest microsoftFamily() {
        var oauth = new FamilyManifest.OAuthDeclaration(
                "https://login.microsoftonline.com/common/oauth2/v2/authorize",
                "https://login.microsoftonline.com/common/oauth2/v2/token",
                "{callback-base-url}/oauth/callback/microsoft",
                List.of("Calendars.ReadWrite"),
                null, null);
        return new FamilyManifest(new FamilyManifest.FamilyDeclaration(
                "microsoft", "Microsoft 365", FamilyManifest.ConsentStrategy.AGGREGATED, oauth, null, List.of()));
    }

    private static FamilyManifest microsoftFamilyWithFlows() {
        var oauth = new FamilyManifest.OAuthDeclaration(
                "https://login.microsoftonline.com/test-tenant/oauth2/v2/authorize",
                "https://login.microsoftonline.com/test-tenant/oauth2/v2/token",
                "{callback-base-url}/oauth/callback/microsoft",
                null, null, null);
        var flows = Map.of(
                "delegated", new FamilyManifest.FlowDeclaration(
                        FamilyManifest.GrantType.AUTHORIZATION_CODE,
                        List.of("Calendars.ReadWrite"), null, null),
                "app", new FamilyManifest.FlowDeclaration(
                        FamilyManifest.GrantType.CLIENT_CREDENTIALS,
                        List.of("https://graph.microsoft.com/.default"), null, null),
                "bot", new FamilyManifest.FlowDeclaration(
                        FamilyManifest.GrantType.CLIENT_CREDENTIALS,
                        List.of("https://api.botframework.com/.default"),
                        "https://login.microsoftonline.com/botframework.com/oauth2/v2.0/token", null));
        return new FamilyManifest(new FamilyManifest.FamilyDeclaration(
                "microsoft", "Microsoft 365", FamilyManifest.ConsentStrategy.AGGREGATED,
                oauth, flows, List.of()));
    }


    private static MutableFacade mutableFacade(Map<String, Map<String, String>> initial) {
        return new MutableFacade(initial);
    }

    static class MutableFacade implements ProviderCredentialFacade {
        private final Map<String, Map<String, String>> data = new ConcurrentHashMap<>();

        MutableFacade(Map<String, Map<String, String>> initial) {
            initial.forEach((k, v) -> data.put(k, new ConcurrentHashMap<>(v)));
        }

        @Override
        public Optional<String> resolve(String providerId, String field) {
            return Optional.ofNullable(data.getOrDefault(providerId, Map.of()).get(field));
        }

        @Override
        public Map<String, String> resolveAll(String providerId) {
            return Map.copyOf(data.getOrDefault(providerId, Map.of()));
        }

        @Override
        public boolean isConfigured(String providerId, List<String> requiredFields) {
            var providerData = data.getOrDefault(providerId, Map.of());
            return requiredFields.stream().allMatch(providerData::containsKey);
        }

        @Override
        public CredentialSource source(String providerId, String field) {
            return resolve(providerId, field).isPresent() ? CredentialSource.CREDENTIAL_STORE : CredentialSource.NONE;
        }

        @Override
        public void store(String providerId, String field, String value) {
            data.computeIfAbsent(providerId, k -> new ConcurrentHashMap<>()).put(field, value);
        }
    }

    static class TestableFamilyConnectionProvider extends FamilyConnectionProvider {
        private String tokenResponse;
        private String lastUrl;
        private String lastBody;

        TestableFamilyConnectionProvider(ProviderCredentialFacade facade, Map<String, FamilyManifest> families) {
            super(facade, families);
        }

        void setTokenResponse(String response) {this.tokenResponse = response;}

        String lastTokenRequestUrl()           {return lastUrl;}

        String lastTokenRequestBody()          {return lastBody;}

        @Override
        String postTokenRequest(String tokenUrl, String body) {
            this.lastUrl  = tokenUrl;
            this.lastBody = body;
            return tokenResponse;
        }
    }

}
