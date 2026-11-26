package io.casehub.connectors.steps;

import io.casehub.connectors.lifecycle.ConnectorManifest;
import io.casehub.connectors.lifecycle.ConnectorManifest.CapabilityDeclaration;
import io.casehub.connectors.lifecycle.ConnectorManifest.ConnectorDeclaration;
import io.casehub.connectors.lifecycle.ConnectorManifest.RequirementDeclaration;
import io.casehub.connectors.lifecycle.ConnectorRegistry;
import io.casehub.connectors.lifecycle.ConnectorStatus;
import io.casehub.connectors.lifecycle.DependencyResolver;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RequireCapabilityPluginTest {

    @Test
    void succeeds_when_provider_is_ready() {
        var resolver = resolver(
                manifest("numbrrs", List.of(), List.of(new CapabilityDeclaration("sms", List.of("receive")))));
        var registry = registryWith("numbrrs", ConnectorStatus.READY);

        var plugin = new RequireCapabilityPlugin("sms", List.of("receive"));
        var result = plugin.execute(resolver, registry);

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.output().get("capability")).isEqualTo("sms");
        assertThat(result.output().get("satisfied")).isEqualTo(true);
        assertThat(result.output().get("provider")).isEqualTo("numbrrs");
    }

    @Test
    void fails_with_options_when_no_provider_ready() {
        var resolver = resolver(
                manifest("numbrrs", List.of(), List.of(new CapabilityDeclaration("sms", List.of("receive")))));
        var registry = registryWith("numbrrs", ConnectorStatus.UNCONFIGURED);

        var plugin = new RequireCapabilityPlugin("sms", List.of("receive"));
        var result = plugin.execute(resolver, registry);

        assertThat(result).isInstanceOf(Result.Failure.class);
        var failure = (Result.Failure) result;
        assertThat(failure.category()).isEqualTo("unmet-requirement");

        @SuppressWarnings("unchecked")
        var options = (List<Map<String, Object>>) failure.detail().get("options");
        assertThat(options).isNotEmpty();
        assertThat(options).anyMatch(o -> "numbrrs".equals(o.get("connector-id")));
        assertThat(options).anyMatch(o -> "manual".equals(o.get("type")));
    }

    @Test
    void fails_with_manual_only_when_no_provider_exists() {
        var resolver = resolver();
        var registry = new ConnectorRegistry();

        var plugin = new RequireCapabilityPlugin("sms", List.of("receive"));
        var result = plugin.execute(resolver, registry);

        assertThat(result).isInstanceOf(Result.Failure.class);
        var failure = (Result.Failure) result;

        @SuppressWarnings("unchecked")
        var options = (List<Map<String, Object>>) failure.detail().get("options");
        assertThat(options).hasSize(1);
        assertThat(options.getFirst().get("type")).isEqualTo("manual");
    }

    @Test
    void succeeds_when_unqualified_provider_satisfies_qualified_requirement() {
        var resolver = resolver(
                manifest("twilio", List.of(), List.of(new CapabilityDeclaration("sms"))));
        var registry = registryWith("twilio", ConnectorStatus.READY);

        var plugin = new RequireCapabilityPlugin("sms", List.of("receive"));
        var result = plugin.execute(resolver, registry);

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.output().get("provider")).isEqualTo("twilio");
    }

    @Test
    void succeeds_with_no_sub_qualifier() {
        var resolver = resolver(
                manifest("twilio", List.of(), List.of(new CapabilityDeclaration("sms"))));
        var registry = registryWith("twilio", ConnectorStatus.READY);

        var plugin = new RequireCapabilityPlugin("sms", null);
        var result = plugin.execute(resolver, registry);

        assertThat(result).isInstanceOf(Result.Success.class);
    }

    private static DependencyResolver resolver(ConnectorManifest... manifests) {
        return new DependencyResolver(List.of(manifests));
    }

    private static ConnectorManifest manifest(String id,
            List<RequirementDeclaration> requires,
            List<CapabilityDeclaration> provides) {
        return new ConnectorManifest(new ConnectorDeclaration(
                id, "test", id, null, null,
                requires.isEmpty() ? null : requires,
                provides.isEmpty() ? null : provides,
                null, null));
    }

    private static ConnectorRegistry registryWith(String id, ConnectorStatus status) {
        var registry = new ConnectorRegistry();
        registry.register(manifest(id, List.of(), List.of()), status);
        return registry;
    }
}
