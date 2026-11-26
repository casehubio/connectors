package io.casehub.connectors.it;

import io.casehub.connectors.lifecycle.ConnectorManifest;
import io.casehub.connectors.lifecycle.ConnectorManifestLoader;
import io.casehub.connectors.lifecycle.ConnectorRegistry;
import io.casehub.connectors.lifecycle.ConnectorStatus;
import io.casehub.connectors.lifecycle.DependencyResolver;
import io.casehub.connectors.lifecycle.ResolutionOption;
import io.casehub.connectors.steps.RequireCapabilityPlugin;
import io.casehub.platform.api.authn.CredentialType;
import io.casehub.platform.api.authn.StaticCredentialRecord;
import io.casehub.platform.api.authn.StaticCredentialStore;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SetupChainIntegrationTest {

    private List<ConnectorManifest> manifests;
    private DependencyResolver resolver;
    private ConnectorRegistry registry;
    private WritableCredentialStore credentialStore;

    @BeforeEach
    void setUp() {
        manifests = ConnectorManifestLoader.discoverFromClasspath();
        resolver = new DependencyResolver(manifests);
        registry = new ConnectorRegistry();
        credentialStore = new WritableCredentialStore();

        for (var m : manifests) {
            registry.register(m, ConnectorStatus.UNCONFIGURED);
        }
    }

    // --- Automated chain: Signal → SMS → Numbrrs → credential input → retry ---

    @Test
    void require_capability_fails_when_numbrrs_unconfigured() {
        var plugin = new RequireCapabilityPlugin("sms", List.of("receive"));
        var result = plugin.execute(resolver, registry);

        assertThat(result).isInstanceOf(Result.Failure.class);
        var failure = (Result.Failure) result;
        assertThat(failure.category()).isEqualTo("unmet-requirement");
        assertThat(failure.detail().get("capability")).isEqualTo("sms");

        @SuppressWarnings("unchecked")
        var options = (List<Map<String, Object>>) failure.detail().get("options");
        assertThat(options).anySatisfy(o -> {
            assertThat(o.get("type")).isEqualTo("provider");
            assertThat(o.get("connector-id")).isEqualTo("numbrrs");
            assertThat(o.get("status")).isEqualTo("UNCONFIGURED");
        });
        assertThat(options).anySatisfy(o ->
                assertThat(o.get("type")).isEqualTo("manual"));
    }

    @Test
    void full_automated_chain_signal_to_numbrrs_credential_input_to_retry() {
        // 1. Signal manifest requires sms[receive]
        var signalManifest = manifests.stream()
                .filter(m -> m.connector().id().equals("signal"))
                .findFirst().orElseThrow();
        assertThat(signalManifest.connector().requires()).isNotEmpty();
        assertThat(signalManifest.connector().requires().getFirst().capability()).isEqualTo("sms");

        // 2. require-capability step fails — Numbrrs exists but is UNCONFIGURED
        var plugin = new RequireCapabilityPlugin("sms", List.of("receive"));
        var firstAttempt = plugin.execute(resolver, registry);
        assertThat(firstAttempt).isInstanceOf(Result.Failure.class);

        // 3. DependencyResolver provides resolution options
        var unmet = resolver.unmetRequirements("signal", registry);
        assertThat(unmet).hasSize(1);
        var requirement = unmet.getFirst();
        assertThat(requirement.reason()).isEqualTo("SMS verification code during registration");

        var providerOption = requirement.options().stream()
                .filter(o -> o instanceof ResolutionOption.ProviderOption)
                .map(o -> (ResolutionOption.ProviderOption) o)
                .findFirst().orElseThrow();
        assertThat(providerOption.connectorId()).isEqualTo("numbrrs");
        assertThat(providerOption.status()).isEqualTo(ConnectorStatus.UNCONFIGURED);

        // 4. Simulate credential-input flow: browser form stores credentials
        //    (CredentialFormService is tested in the graphql module;
        //     here we verify the chain effect on the registry)
        var now = Instant.now();
        credentialStore.store(new StaticCredentialRecord(
                "system", "default", "numbrrs.api-key",
                "real-numbrrs-key-123", CredentialType.API_KEY, now, now));
        credentialStore.store(new StaticCredentialRecord(
                "system", "default", "numbrrs.numbers",
                "+447700900001,+447700900002", CredentialType.API_KEY, now, now));

        // 5. Verify credentials stored — check provider keys, not values
        assertThat(credentialStore.stored).hasSizeGreaterThanOrEqualTo(2);
        assertThat(credentialStore.stored).anySatisfy(r ->
                assertThat(r.provider()).isEqualTo("numbrrs.api-key"));
        assertThat(credentialStore.stored).anySatisfy(r ->
                assertThat(r.provider()).isEqualTo("numbrrs.numbers"));

        // 6. Simulate lifecycle refresh — Numbrrs becomes READY
        var numbrrsManifest = manifests.stream()
                .filter(m -> m.connector().id().equals("numbrrs"))
                .findFirst().orElseThrow();
        registry.register(numbrrsManifest, ConnectorStatus.READY);

        // 7. Retry — require-capability now succeeds
        var retry = plugin.execute(resolver, registry);
        assertThat(retry).isInstanceOf(Result.Success.class);
        assertThat(retry.output().get("capability")).isEqualTo("sms");
        assertThat(retry.output().get("satisfied")).isEqualTo(true);
        assertThat(retry.output().get("provider")).isEqualTo("numbrrs");
    }

    // --- Manual path ---

    @Test
    void manual_option_available_for_sms_capability() {
        var unmet = resolver.unmetRequirements("signal", registry);
        assertThat(unmet).hasSize(1);

        var manualOption = unmet.getFirst().options().stream()
                .filter(o -> o instanceof ResolutionOption.ManualOption)
                .map(o -> (ResolutionOption.ManualOption) o)
                .findFirst().orElseThrow();
        assertThat(manualOption.capability()).isEqualTo("sms");
    }

    @Test
    void manual_path_bypasses_numbrrs_setup() {
        // Manual path: user provides own number directly — no Numbrrs setup needed.
        // The platform playbook runner (platform#987) handles ManualOption flow.
        // Here we verify the resolution structure supports it.

        var unmet = resolver.unmetRequirements("signal", registry);
        var options = unmet.getFirst().options();

        // Both provider and manual options are presented
        assertThat(options).anySatisfy(o ->
                assertThat(o).isInstanceOf(ResolutionOption.ProviderOption.class));
        assertThat(options).anySatisfy(o ->
                assertThat(o).isInstanceOf(ResolutionOption.ManualOption.class));

        // Numbrrs remains UNCONFIGURED — the manual path doesn't require it
        assertThat(registry.status("numbrrs")).isEqualTo(ConnectorStatus.UNCONFIGURED);
    }

    @Test
    void numbrrs_manifest_declares_help_and_examples() {
        var numbrrsManifest = manifests.stream()
                .filter(m -> m.connector().id().equals("numbrrs"))
                .findFirst().orElseThrow();
        var fields = numbrrsManifest.connector().auth().fields();

        var apiKey = fields.stream().filter(f -> f.name().equals("api-key")).findFirst().orElseThrow();
        assertThat(apiKey.help()).contains("Numbrrs dashboard");
        assertThat(apiKey.example()).isEqualTo("nmbr_sk_1a2b3c4d5e6f");
        assertThat(apiKey.isSensitive()).isTrue();

        var numbers = fields.stream().filter(f -> f.name().equals("numbers")).findFirst().orElseThrow();
        assertThat(numbers.isSensitive()).isFalse();
        assertThat(numbers.isRequired()).isFalse();
    }

    // --- Test credential store ---

    static class WritableCredentialStore implements StaticCredentialStore {
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
