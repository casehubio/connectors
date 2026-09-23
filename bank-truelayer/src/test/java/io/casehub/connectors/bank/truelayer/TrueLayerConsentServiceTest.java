package io.casehub.connectors.bank.truelayer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrueLayerConsentServiceTest {

    TrueLayerConsentService service;

    @BeforeEach
    void setUp() {
        service = new TrueLayerConsentService(
                "test-client-id", "test-client-secret",
                "https://auth.truelayer-sandbox.com", null,
                new InMemoryConsentTokenStore());
    }

    @Test
    void generateAuthLink_returnsUrlWithState() {
        AuthLink link = service.generateAuthLink("user-1",
                List.of(ConsentScope.ACCOUNTS, ConsentScope.BALANCE),
                "https://app.example.com/callback");
        assertThat(link.authorizationUrl())
                .contains("client_id=test-client-id")
                .contains("redirect_uri=")
                .contains("state=");
    }

    @Test
    void generateAuthLink_stateIsUnique() {
        AuthLink link1 = service.generateAuthLink("user-1",
                List.of(ConsentScope.ACCOUNTS), "https://app.example.com/callback");
        AuthLink link2 = service.generateAuthLink("user-1",
                List.of(ConsentScope.ACCOUNTS), "https://app.example.com/callback");
        String state1 = extractState(link1.authorizationUrl());
        String state2 = extractState(link2.authorizationUrl());
        assertThat(state1).isNotEqualTo(state2);
    }

    @Test
    void exchangeCode_unknownState_throws() {
        assertThatThrownBy(() -> service.exchangeCode("auth-code", "bogus-state"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("Unknown or expired");
    }

    @Test
    void getUserToken_noConsent_returnsNull() {
        assertThat(service.getUserToken("nonexistent-user")).isNull();
    }

    @Test
    void storeAndRetrieveConsent() {
        service.storeConsent("user-1", new StoredConsent(
                "access-token-1", "refresh-token-1",
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(86400 * 90),
                List.of(ConsentScope.ACCOUNTS, ConsentScope.BALANCE),
                Instant.now()));

        assertThat(service.getUserToken("user-1")).isEqualTo("access-token-1");
    }

    @Test
    void getUserToken_expiredAccessToken_returnsNull_whenNoRefresh() {
        service.storeConsent("user-1", new StoredConsent(
                "expired-token", null,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(86400 * 90),
                List.of(ConsentScope.ACCOUNTS),
                Instant.now()));

        assertThat(service.getUserToken("user-1")).isNull();
    }

    @Test
    void consentStatus_activeConsent() {
        service.storeConsent("user-1", new StoredConsent(
                "token", "refresh",
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(86400 * 90),
                List.of(ConsentScope.ACCOUNTS),
                Instant.now()));

        ConsentInfo info = service.consentStatus("user-1");
        assertThat(info.status()).isEqualTo(ConsentStatus.ACTIVE);
        assertThat(info.userId()).isEqualTo("user-1");
    }

    @Test
    void consentStatus_noConsent_returnsNull() {
        assertThat(service.consentStatus("unknown")).isNull();
    }

    @Test
    void revokeConsent_removesStoredConsent() {
        service.storeConsent("user-1", new StoredConsent(
                "token", "refresh",
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(86400 * 90),
                List.of(ConsentScope.ACCOUNTS),
                Instant.now()));

        service.revokeConsent("user-1");
        assertThat(service.getUserToken("user-1")).isNull();
        assertThat(service.consentStatus("user-1")).isNull();
    }

    private String extractState(String url) {
        int stateIdx = url.indexOf("state=");
        if (stateIdx < 0) return "";
        String rest = url.substring(stateIdx + 6);
        int ampIdx = rest.indexOf('&');
        return ampIdx > 0 ? rest.substring(0, ampIdx) : rest;
    }
}
