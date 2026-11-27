package io.casehub.connectors.lifecycle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PersistentCredentialFacadeTest {

    @TempDir Path tempDir;
    private Path credentialsFile;

    @BeforeEach
    void setUp() {
        credentialsFile = tempDir.resolve("credentials.yaml");
    }

    // --- loading ---

    @Test
    void loads_credentials_from_yaml_file() throws IOException {
        Files.writeString(credentialsFile, """
                google:
                  client-id: google-id-123
                  client-secret: google-secret-456
                numbrrs:
                  api-key: nmbr_sk_test
                """);

        var facade = new PersistentCredentialFacade(credentialsFile);

        assertThat(facade.resolve("google", "client-id")).hasValue("google-id-123");
        assertThat(facade.resolve("google", "client-secret")).hasValue("google-secret-456");
        assertThat(facade.resolve("numbrrs", "api-key")).hasValue("nmbr_sk_test");
    }

    @Test
    void returns_empty_for_unknown_provider() {
        var facade = new PersistentCredentialFacade(credentialsFile);

        assertThat(facade.resolve("unknown", "field")).isEmpty();
    }

    @Test
    void returns_empty_for_unknown_field() throws IOException {
        Files.writeString(credentialsFile, """
                google:
                  client-id: test
                """);

        var facade = new PersistentCredentialFacade(credentialsFile);

        assertThat(facade.resolve("google", "unknown")).isEmpty();
    }

    @Test
    void creates_empty_facade_when_file_does_not_exist() {
        var facade = new PersistentCredentialFacade(credentialsFile);

        assertThat(facade.resolve("anything", "field")).isEmpty();
        assertThat(facade.resolveAll("anything")).isEmpty();
    }

    // --- store ---

    @Test
    void store_persists_to_yaml_file() {
        var facade = new PersistentCredentialFacade(credentialsFile);

        facade.store("google", "access-token", "ya29.test-token");

        assertThat(facade.resolve("google", "access-token")).hasValue("ya29.test-token");
        assertThat(credentialsFile).exists();
        assertThat(credentialsFile).content().contains("access-token");
    }

    @Test
    void store_adds_to_existing_provider() throws IOException {
        Files.writeString(credentialsFile, """
                google:
                  client-id: existing-id
                """);

        var facade = new PersistentCredentialFacade(credentialsFile);
        facade.store("google", "refresh-token", "1//test-refresh");

        assertThat(facade.resolve("google", "client-id")).hasValue("existing-id");
        assertThat(facade.resolve("google", "refresh-token")).hasValue("1//test-refresh");
    }

    @Test
    void store_adds_new_provider() throws IOException {
        Files.writeString(credentialsFile, """
                google:
                  client-id: existing
                """);

        var facade = new PersistentCredentialFacade(credentialsFile);
        facade.store("microsoft", "tenant-id", "my-tenant");

        assertThat(facade.resolve("google", "client-id")).hasValue("existing");
        assertThat(facade.resolve("microsoft", "tenant-id")).hasValue("my-tenant");
    }

    @Test
    void store_overwrites_existing_field() throws IOException {
        Files.writeString(credentialsFile, """
                google:
                  access-token: old-token
                """);

        var facade = new PersistentCredentialFacade(credentialsFile);
        facade.store("google", "access-token", "new-token");

        assertThat(facade.resolve("google", "access-token")).hasValue("new-token");
    }

    @Test
    void stored_credentials_survive_reload() {
        var facade = new PersistentCredentialFacade(credentialsFile);
        facade.store("google", "client-id", "persisted-id");
        facade.store("google", "client-secret", "persisted-secret");

        var reloaded = new PersistentCredentialFacade(credentialsFile);

        assertThat(reloaded.resolve("google", "client-id")).hasValue("persisted-id");
        assertThat(reloaded.resolve("google", "client-secret")).hasValue("persisted-secret");
    }

    // --- resolveAll ---

    @Test
    void resolveAll_returns_all_fields_for_provider() throws IOException {
        Files.writeString(credentialsFile, """
                google:
                  client-id: id
                  client-secret: secret
                """);

        var facade = new PersistentCredentialFacade(credentialsFile);

        assertThat(facade.resolveAll("google"))
                .containsEntry("client-id", "id")
                .containsEntry("client-secret", "secret")
                .hasSize(2);
    }

    // --- isConfigured ---

    @Test
    void isConfigured_true_when_all_required_fields_present() throws IOException {
        Files.writeString(credentialsFile, """
                google:
                  client-id: id
                  client-secret: secret
                """);

        var facade = new PersistentCredentialFacade(credentialsFile);

        assertThat(facade.isConfigured("google", List.of("client-id", "client-secret"))).isTrue();
    }

    @Test
    void isConfigured_false_when_field_missing() throws IOException {
        Files.writeString(credentialsFile, """
                google:
                  client-id: id
                """);

        var facade = new PersistentCredentialFacade(credentialsFile);

        assertThat(facade.isConfigured("google", List.of("client-id", "client-secret"))).isFalse();
    }

    @Test
    void isConfigured_reflects_runtime_stores() {
        var facade = new PersistentCredentialFacade(credentialsFile);

        assertThat(facade.isConfigured("google", List.of("access-token"))).isFalse();
        facade.store("google", "access-token", "ya29.test");
        assertThat(facade.isConfigured("google", List.of("access-token"))).isTrue();
    }

    // --- source ---

    @Test
    void source_returns_credential_store_when_present() throws IOException {
        Files.writeString(credentialsFile, """
                google:
                  client-id: id
                """);

        var facade = new PersistentCredentialFacade(credentialsFile);

        assertThat(facade.source("google", "client-id"))
                .isEqualTo(ProviderCredentialFacade.CredentialSource.CREDENTIAL_STORE);
    }

    @Test
    void source_returns_none_when_absent() {
        var facade = new PersistentCredentialFacade(credentialsFile);

        assertThat(facade.source("google", "client-id"))
                .isEqualTo(ProviderCredentialFacade.CredentialSource.NONE);
    }

    // --- file creation ---

    @Test
    void store_creates_parent_directories() {
        var nested = tempDir.resolve("sub/dir/credentials.yaml");
        var facade = new PersistentCredentialFacade(nested);

        facade.store("google", "client-id", "test");

        assertThat(nested).exists();
        assertThat(facade.resolve("google", "client-id")).hasValue("test");
    }
}
