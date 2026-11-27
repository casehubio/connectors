package io.casehub.connectors.lifecycle.deployment;

import io.casehub.connectors.lifecycle.ConnectorRegistry;
import io.casehub.connectors.lifecycle.InMemoryCredentialFacade;
import io.casehub.connectors.lifecycle.ProviderCredentialFacade;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorValueArgTest {

    private static final String MANIFEST = """
            connector:
              id: test-value
              platform: test
              description: "Test value arg"
              auth:
                type: api-key
                fields:
                  - name: api-key
                    description: "API key"
              produces:
                - class: io.casehub.connectors.lifecycle.deployment.TestPlatformWithId
                  type: platform
                  qualifier: test-named
                  constructor-args:
                    - field: api-key
                    - value: my-platform-id
            """;

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar
                    .addClass(TestPlatformWithId.class)
                    .addClass(TestCredentials.class)
                    .addClass(ConnectorLifecycleProcessor.class)
                    .addClass(ConnectorBeanCreator.class)
                    .addClass(RegistryBeanCreator.class)
                    .addClass(DefaultFacadeCreator.class)
                    .addClass(DependencyResolverCreator.class)
                    .addClass(ManifestBuildItem.class)
                    .addAsResource(new StringAsset(MANIFEST), "META-INF/connector-manifest.yaml")
                    .addAsResource(new StringAsset(
                            "io.casehub.connectors.lifecycle.deployment.ConnectorLifecycleProcessor\n"),
                            "META-INF/quarkus-build-steps.list"));

    @Inject
    @Named("test-named")
    TestPlatformWithId testPlatform;

    @Inject
    ConnectorRegistry registry;

    @Test
    void value_arg_is_passed_as_literal_string() {
        assertThat(testPlatform).isNotNull();
        assertThat(testPlatform.platformId()).isEqualTo("my-platform-id");
    }

    @Test
    void field_arg_is_resolved_from_credentials() {
        assertThat(testPlatform.apiKey()).isEqualTo("test-key");
    }

    @Test
    void qualifier_produces_named_bean() {
        assertThat(testPlatform).isNotNull();
    }

    public static class TestCredentials {
        @Produces
        @Singleton
        ProviderCredentialFacade facade() {
            return new InMemoryCredentialFacade(Map.of(
                    "test-value", Map.of("api-key", "test-key")));
        }
    }
}
