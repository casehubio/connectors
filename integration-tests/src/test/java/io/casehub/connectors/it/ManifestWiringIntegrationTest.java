package io.casehub.connectors.it;

import io.casehub.connectors.lifecycle.ConnectorManifestLoader;
import io.casehub.connectors.lifecycle.ConnectorRegistry;
import io.casehub.connectors.lifecycle.ConnectorStatus;
import io.casehub.connectors.lifecycle.DependencyResolver;
import io.casehub.connectors.lifecycle.InMemoryCredentialFacade;
import io.casehub.connectors.lifecycle.ProviderCredentialFacade;
import io.casehub.connectors.lifecycle.ResolutionOption;
import io.casehub.connectors.mobile.numbrrs.NumbrrsInboundConnector;
import io.casehub.connectors.mobile.numbrrs.NumbrrsMobilePlatform;
import io.casehub.connectors.mobile.spi.MobilePlatform;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(ManifestWiringIntegrationTest.Profile.class)
class ManifestWiringIntegrationTest {

    @Inject
    ConnectorRegistry registry;

    @Inject
    NumbrrsMobilePlatform numbrrsPlatform;

    @Inject
    NumbrrsInboundConnector numbrrsInbound;

    @Test
    void registry_discovers_numbrrs_manifest() {
        assertThat(registry).isNotNull();
        assertThat(registry.all()).isNotEmpty();
        assertThat(registry.manifest("numbrrs")).isPresent();
        assertThat(registry.manifest("numbrrs").get().connector().platform()).isEqualTo("mobile");
    }

    @Test
    void registry_reports_ready_when_credentials_configured() {
        assertThat(registry.status("numbrrs")).isEqualTo(ConnectorStatus.READY);
    }

    @Test
    void numbrrs_platform_is_injectable_with_resolved_credentials() {
        assertThat(numbrrsPlatform).isNotNull();
        assertThat(numbrrsPlatform.id()).isEqualTo("numbrrs");
        assertThat(numbrrsPlatform.supports(MobilePlatform.Sms.class)).isTrue();
        assertThat(numbrrsPlatform.supports(MobilePlatform.NumberInfo.class)).isTrue();
    }

    @Test
    void numbrrs_inbound_connector_is_injectable() {
        assertThat(numbrrsInbound).isNotNull();
        assertThat(numbrrsInbound.id()).isEqualTo("numbrrs-sms-inbound");
    }

    @Test
    void credential_facade_flows_through_to_number_info() {
        var numbers = numbrrsPlatform.numberInfo().listNumbers();
        assertThat(numbers).hasSize(2);
        assertThat(numbers.get(0).number()).isEqualTo("+441234000001");
        assertThat(numbers.get(1).number()).isEqualTo("+441234000002");
    }

    @Test
    void resolver_discovers_signal_requires_sms_from_real_manifests() {
        var manifests = ConnectorManifestLoader.discoverFromClasspath();
        var resolver  = new DependencyResolver(manifests);

        var unmet = resolver.unmetRequirements("signal", registry);
        assertThat(unmet).isEmpty();
    }

    @Test
    void resolver_reports_unmet_when_provider_unconfigured() {
        var manifests = ConnectorManifestLoader.discoverFromClasspath();
        var resolver  = new DependencyResolver(manifests);

        var emptyRegistry = new ConnectorRegistry();
        var unmet         = resolver.unmetRequirements("signal", emptyRegistry);

        assertThat(unmet).hasSize(1);
        assertThat(unmet.getFirst().capability()).isEqualTo("sms");
        assertThat(unmet.getFirst().options()).anyMatch(o -> o instanceof ResolutionOption.ProviderOption p
                                                             && p.connectorId().equals("numbrrs"));
        assertThat(unmet.getFirst().options()).anyMatch(o -> o instanceof ResolutionOption.ManualOption);
    }


    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.arc.exclude-types",
                    "io.casehub.connectors.chat.**," +
                    "io.casehub.connectors.signal.**," +
                    "io.casehub.connectors.email.**," +
                    "io.casehub.connectors.document.**," +
                    "io.casehub.connectors.calendar.**," +
                    "io.casehub.connectors.mobile.twilio.**," +
                    "io.casehub.connectors.mobile.ref.**," +
                    "io.casehub.connectors.microsoft.**," +
                    "io.casehub.connectors.webhook.**," +
                    "io.casehub.connectors.voice.**");
        }
    }

    @ApplicationScoped
    public static class TestCredentialProducer {

        @Produces
        @Singleton
        ProviderCredentialFacade facade() {
            return new InMemoryCredentialFacade(Map.of(
                    "numbrrs", Map.of(
                            "api-key", "test-key-integration",
                            "numbers", "+441234000001,+441234000002")));
        }
    }
}
