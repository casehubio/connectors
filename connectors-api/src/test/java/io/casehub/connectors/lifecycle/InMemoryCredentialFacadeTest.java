package io.casehub.connectors.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCredentialFacadeTest {

    @Test
    void resolves_credential_from_store() {
        var facade = new InMemoryCredentialFacade(Map.of(
                "numbrrs", Map.of("api-key", "test-key-123", "signing-secret", "secret-456")));

        assertThat(facade.resolve("numbrrs", "api-key")).contains("test-key-123");
        assertThat(facade.resolve("numbrrs", "signing-secret")).contains("secret-456");
        assertThat(facade.resolve("numbrrs", "missing")).isEmpty();
        assertThat(facade.resolve("unknown", "api-key")).isEmpty();
    }

    @Test
    void resolveAll_returns_all_fields_for_provider() {
        var facade = new InMemoryCredentialFacade(Map.of(
                "numbrrs", Map.of("api-key", "key", "numbers", "+447700100001")));

        assertThat(facade.resolveAll("numbrrs")).containsEntry("api-key", "key");
        assertThat(facade.resolveAll("numbrrs")).containsEntry("numbers", "+447700100001");
        assertThat(facade.resolveAll("unknown")).isEmpty();
    }

    @Test
    void isConfigured_checks_required_fields() {
        var facade = new InMemoryCredentialFacade(Map.of(
                "numbrrs", Map.of("api-key", "key")));

        assertThat(facade.isConfigured("numbrrs", List.of("api-key"))).isTrue();
        assertThat(facade.isConfigured("numbrrs", List.of("api-key", "signing-secret"))).isFalse();
        assertThat(facade.isConfigured("unknown", List.of("api-key"))).isFalse();
    }

    @Test
    void source_returns_credential_store_when_present() {
        var facade = new InMemoryCredentialFacade(Map.of(
                "numbrrs", Map.of("api-key", "key")));

        assertThat(facade.source("numbrrs", "api-key"))
                .isEqualTo(ProviderCredentialFacade.CredentialSource.CREDENTIAL_STORE);
        assertThat(facade.source("numbrrs", "missing"))
                .isEqualTo(ProviderCredentialFacade.CredentialSource.NONE);
    }
}
