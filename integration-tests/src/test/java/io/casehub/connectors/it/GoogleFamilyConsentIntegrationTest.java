package io.casehub.connectors.it;

import io.casehub.connectors.lifecycle.ConnectorManifest;
import io.casehub.connectors.lifecycle.ConnectorManifest.AuthDeclaration;
import io.casehub.connectors.lifecycle.ConnectorManifest.CapabilityDeclaration;
import io.casehub.connectors.lifecycle.ConnectorManifest.ConnectorDeclaration;
import io.casehub.connectors.lifecycle.ConnectorManifestLoader;
import io.casehub.connectors.lifecycle.ConnectorRegistry;
import io.casehub.connectors.lifecycle.ConnectorStatus;
import io.casehub.connectors.lifecycle.DependencyResolver;
import io.casehub.connectors.lifecycle.FamilyManifest;
import io.casehub.connectors.lifecycle.PersistentCredentialFacade;
import io.casehub.connectors.lifecycle.ResolutionOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleFamilyConsentIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void family_consent_activates_all_google_connectors() {
        var facade = new PersistentCredentialFacade(tempDir.resolve("credentials.yaml"));
        var families = Map.of("google", googleFamily());
        var resolved = ConnectorManifestLoader.resolveFamilies(buildGoogleManifests(), families);

        assertThat(resolved).hasSize(4);
        for (var m : resolved) {
            assertThat(m.connector().auth().oauth()).isNotNull();
            assertThat(m.connector().credentialKey()).isEqualTo("google");
        }

        var registry = new ConnectorRegistry(facade);
        for (var m : resolved) {
            registry.register(m, ConnectorStatus.UNCONFIGURED);
        }

        var resolver = new DependencyResolver(resolved, facade);
        for (var m : resolved) {
            assertThat(resolver.unmetRequirements(m.connector().id(), registry))
                    .as("Before consent: " + m.connector().id()).hasSize(1);
        }

        facade.store("google", "client-id", "test-client-id");
        facade.store("google", "client-secret", "test-secret");
        facade.store("google", "access-token", "ya29.test-access");
        facade.store("google", "refresh-token", "1//test-refresh");
        facade.store("google", "expires-at", Instant.now().plusSeconds(3600).toString());
        facade.store("google", "granted-scopes", "calendar.readonly drive.readonly gmail.readonly contacts.readonly");

        registry.updateStatusByCredentialKey("google");

        var googleConnectors = registry.all().stream()
                .filter(r -> "google".equals(r.manifest().connector().credentialKey()))
                .toList();
        assertThat(googleConnectors).hasSize(4);

        for (var r : googleConnectors) {
            assertThat(resolver.unmetRequirements(r.manifest().connector().id(), registry))
                    .as("After consent: " + r.manifest().connector().id()).isEmpty();
        }
    }

    @Test
    void dependency_resolver_shows_unmet_before_consent() {
        var facade = new PersistentCredentialFacade(tempDir.resolve("credentials.yaml"));
        var resolved = ConnectorManifestLoader.resolveFamilies(buildGoogleManifests(), Map.of("google", googleFamily()));
        var registry = new ConnectorRegistry(facade);
        for (var m : resolved) {
            registry.register(m, ConnectorStatus.UNCONFIGURED);
        }

        var resolver = new DependencyResolver(resolved, facade);
        var unmet = resolver.unmetRequirements("calendar-google", registry);

        assertThat(unmet).hasSize(1);
        assertThat(unmet.getFirst().capability()).isEqualTo("oauth2");
        assertThat(unmet.getFirst().sub()).containsExactly("google");
        assertThat(unmet.getFirst().options())
                .hasAtLeastOneElementOfType(ResolutionOption.FamilyOAuthOption.class);
    }

    private static FamilyManifest googleFamily() {
        var oauth = new FamilyManifest.OAuthDeclaration(
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.com/token",
                "{callback-base-url}/oauth/callback/google",
                List.of("https://www.googleapis.com/auth/calendar.readonly",
                        "https://www.googleapis.com/auth/calendar.events",
                        "https://www.googleapis.com/auth/drive.readonly",
                        "https://www.googleapis.com/auth/drive.file",
                        "https://www.googleapis.com/auth/gmail.readonly",
                        "https://www.googleapis.com/auth/gmail.modify",
                        "https://www.googleapis.com/auth/contacts.readonly",
                        "https://www.googleapis.com/auth/contacts"),
                "offline", "consent");
        return new FamilyManifest(new FamilyManifest.FamilyDeclaration(
                "google", "Google Workspace — shared OAuth2 consent",
                FamilyManifest.ConsentStrategy.INCREMENTAL, oauth, null,
                List.of(new ConnectorManifest.FieldDeclaration("client-id", false, "Google OAuth2 client ID", false, null, null, null),
                        new ConnectorManifest.FieldDeclaration("client-secret", true, "Google OAuth2 client secret", true, null, null, null))));
    }

    private static List<ConnectorManifest> buildGoogleManifests() {
        return List.of(
                googleManifest("calendar-google", "calendar", "Google Calendar",
                        List.of(new CapabilityDeclaration("calendar"))),
                googleManifest("document-google", "document", "Google Drive",
                        List.of(new CapabilityDeclaration("document", List.of("files", "folders", "search", "sharing")))),
                googleManifest("contacts-google", "contacts", "Google Contacts",
                        List.of(new CapabilityDeclaration("contacts", List.of("read", "groups", "write")))),
                googleManifest("email-google", "email", "Gmail",
                        List.of(new CapabilityDeclaration("email", List.of("receive", "search")))));
    }

    private static ConnectorManifest googleManifest(String id, String platform,
            String description, List<CapabilityDeclaration> provides) {
        return new ConnectorManifest(new ConnectorDeclaration(
                id, platform, description,
                new AuthDeclaration("oauth2", "google", null, List.of()),
                null, null, provides, null, null));
    }
}
