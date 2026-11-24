package io.casehub.connectors.lifecycle.deployment;

import io.casehub.connectors.lifecycle.ConnectorRegistry;
import io.casehub.connectors.lifecycle.ConnectorStatus;
import io.casehub.connectors.lifecycle.InMemoryCredentialFacade;
import io.casehub.connectors.lifecycle.ProviderCredentialFacade;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorLifecycleProcessorTest {

    private static final String TEST_MANIFEST = """
            connector:
              id: test-lifecycle
              platform: test
              description: "Test lifecycle connector"
              auth:
                type: api-key
                fields:
                  - name: api-key
                    description: "Test API key"
              produces:
                - class: io.casehub.connectors.lifecycle.deployment.TestPlatform
                  type: platform
                  constructor-args:
                    - field: api-key
            """;

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar
                    .addClass(TestPlatform.class)
                    .addClass(TestCredentialProducer.class)
                    .addClass(ConnectorLifecycleProcessor.class)
                    .addClass(ConnectorBeanCreator.class)
                    .addClass(RegistryBeanCreator.class)
                    .addClass(DefaultFacadeCreator.class)
                    .addClass(ManifestBuildItem.class)
                    .addAsResource(new StringAsset(TEST_MANIFEST), "META-INF/connector-manifest.yaml")
                    .addAsResource(new StringAsset(
                            "io.casehub.connectors.lifecycle.deployment.ConnectorLifecycleProcessor\n"),
                            "META-INF/quarkus-build-steps.list"));

    @Inject
    ConnectorRegistry registry;

    @Inject
    TestPlatform testPlatform;

    @Test
    void registry_is_injectable_and_populated() {
        assertThat(registry).isNotNull();
        assertThat(registry.all()).isNotEmpty();
        assertThat(registry.status("test-lifecycle")).isNotNull();
    }

    @Test
    void registry_reports_ready_when_credentials_configured() {
        assertThat(registry.status("test-lifecycle")).isEqualTo(ConnectorStatus.READY);
    }

    @Test
    void manifest_is_accessible_from_registry() {
        var manifest = registry.manifest("test-lifecycle");
        assertThat(manifest).isPresent();
        assertThat(manifest.get().connector().platform()).isEqualTo("test");
        assertThat(manifest.get().connector().auth().type()).isEqualTo("api-key");
    }

    @Test
    void synthetic_bean_is_injectable_with_resolved_credentials() {
        assertThat(testPlatform).isNotNull();
        assertThat(testPlatform.apiKey()).isEqualTo("test-key-123");
    }

    public static class TestCredentialProducer {

        @Produces
        @Singleton
        ProviderCredentialFacade facade() {
            return new InMemoryCredentialFacade(Map.of(
                    "test-lifecycle", Map.of("api-key", "test-key-123")));
        }
    }
}
