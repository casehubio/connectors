package io.casehub.connectors.graphql;

import io.casehub.connectors.lifecycle.ConnectorRegistry;
import io.casehub.connectors.lifecycle.FamilyManifest;
import io.casehub.connectors.lifecycle.OAuthConfigRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthCallbackEndpointTest {

    private OAuthCallbackEndpoint endpoint;
    private OAuthConsentServiceTest.TestableOAuthConsentService consentService;

    @BeforeEach
    void setUp() {
        var googleOauth = new FamilyManifest.OAuthDeclaration(
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.com/token",
                "{callback-base-url}/oauth/callback/google",
                List.of("https://www.googleapis.com/auth/calendar.readonly"),
                "offline", "consent");
        var oauthConfigs = new OAuthConfigRegistry(Map.of("google", googleOauth));

        var credentialStore = new CredentialFormServiceTest.RecordingCredentialStore();
        var credentialFacade = new OAuthConsentServiceTest.StoreBackedFacade(credentialStore, Map.of(
                "google", Map.of("client-id", "test-id", "client-secret", "test-secret")));
        var registry = new ConnectorRegistry(credentialFacade);

        consentService = new OAuthConsentServiceTest.TestableOAuthConsentService(
                oauthConfigs, credentialFacade, credentialStore, registry,
                "http://localhost:8080");

        endpoint = new OAuthCallbackEndpoint();
        endpoint.consentService = consentService;
    }

    @Test
    void callback_with_valid_code_and_state_returns_success_html() {
        consentService.setTokenResponse("""
                {"access_token":"ya29.test","refresh_token":"1//test","expires_in":3600}""");
        var request = consentService.buildAuthorizationUrl("google");

        var result = endpoint.handleCallback("google", request.state(), "auth-code-123", null);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.html()).contains("configured");
        assertThat(result.html()).contains("google");
    }

    @Test
    void callback_with_error_parameter_returns_error_html() {
        var result = endpoint.handleCallback("google", "some-state", null, "access_denied");

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.html()).contains("access_denied");
    }

    @Test
    void callback_with_invalid_state_returns_400() {
        var result = endpoint.handleCallback("google", "bogus-state", "some-code", null);

        assertThat(result.status()).isEqualTo(400);
        assertThat(result.html()).contains("expired");
    }

    @Test
    void callback_missing_code_and_error_returns_400() {
        var result = endpoint.handleCallback("google", "some-state", null, null);

        assertThat(result.status()).isEqualTo(400);
    }

    @Test
    void callback_error_page_escapes_html_in_error_parameter() {
        var result = endpoint.handleCallback("google", "s", null, "<script>alert(1)</script>");

        assertThat(result.html()).doesNotContain("<script>");
        assertThat(result.html()).contains("&lt;script&gt;");
    }

    @Test
    void callback_with_failed_exchange_returns_error_html() {
        consentService.setTokenResponse(null);

        var request = consentService.buildAuthorizationUrl("google");
        var result = endpoint.handleCallback("google", request.state(), "auth-code", null);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.html()).contains("failed");
    }
}
