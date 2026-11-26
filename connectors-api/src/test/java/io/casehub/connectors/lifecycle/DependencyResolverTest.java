package io.casehub.connectors.lifecycle;

import io.casehub.connectors.lifecycle.ConnectorManifest.CapabilityDeclaration;
import io.casehub.connectors.lifecycle.ConnectorManifest.ConnectorDeclaration;
import io.casehub.connectors.lifecycle.ConnectorManifest.RequirementDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyResolverTest {

    @Test
    void qualified_requirement_matched_by_qualified_provider() {
        var signal = manifest("signal", "chat",
                List.of(new RequirementDeclaration("sms", List.of("receive"), "Verification")),
                List.of());
        var numbrrs = manifest("numbrrs", "mobile",
                List.of(),
                List.of(new CapabilityDeclaration("sms", List.of("receive"))));

        var resolver = new DependencyResolver(List.of(signal, numbrrs));
        var registry = registryWith(Map.of("numbrrs", ConnectorStatus.READY));

        var unmet = resolver.unmetRequirements("signal", registry);
        assertThat(unmet).isEmpty();
    }

    @Test
    void unqualified_provider_satisfies_qualified_requirement() {
        var signal = manifest("signal", "chat",
                List.of(new RequirementDeclaration("sms", List.of("receive"), "Verification")),
                List.of());
        var twilio = manifest("twilio", "mobile",
                List.of(),
                List.of(new CapabilityDeclaration("sms")));

        var resolver = new DependencyResolver(List.of(signal, twilio));
        var registry = registryWith(Map.of("twilio", ConnectorStatus.READY));

        var unmet = resolver.unmetRequirements("signal", registry);
        assertThat(unmet).isEmpty();
    }

    @Test
    void no_ready_provider_returns_unmet_with_options() {
        var signal = manifest("signal", "chat",
                List.of(new RequirementDeclaration("sms", List.of("receive"), "Verification")),
                List.of());
        var numbrrs = manifest("numbrrs", "mobile",
                List.of(),
                List.of(new CapabilityDeclaration("sms", List.of("receive"))));

        var resolver = new DependencyResolver(List.of(signal, numbrrs));
        var registry = registryWith(Map.of("numbrrs", ConnectorStatus.UNCONFIGURED));

        var unmet = resolver.unmetRequirements("signal", registry);
        assertThat(unmet).hasSize(1);
        assertThat(unmet.getFirst().capability()).isEqualTo("sms");

        var options = unmet.getFirst().options();
        assertThat(options).hasAtLeastOneElementOfType(ResolutionOption.ProviderOption.class);
        assertThat(options).hasAtLeastOneElementOfType(ResolutionOption.ManualOption.class);

        var provider = options.stream()
                .filter(o -> o instanceof ResolutionOption.ProviderOption)
                .map(o -> (ResolutionOption.ProviderOption) o)
                .findFirst().orElseThrow();
        assertThat(provider.connectorId()).isEqualTo("numbrrs");
        assertThat(provider.status()).isEqualTo(ConnectorStatus.UNCONFIGURED);
    }

    @Test
    void manual_option_always_present_in_resolution() {
        var signal = manifest("signal", "chat",
                List.of(new RequirementDeclaration("sms", List.of("receive"), "Verification")),
                List.of());

        var resolver = new DependencyResolver(List.of(signal));
        var options = resolver.resolutionOptions("sms", List.of("receive"), registryWith(Map.of()));

        assertThat(options).hasSize(1);
        assertThat(options.getFirst()).isInstanceOf(ResolutionOption.ManualOption.class);
        assertThat(((ResolutionOption.ManualOption) options.getFirst()).capability()).isEqualTo("sms");
    }

    @Test
    void connector_with_no_requirements_has_no_unmet() {
        var telegram = manifest("telegram", "chat", List.of(), List.of());
        var resolver = new DependencyResolver(List.of(telegram));

        var unmet = resolver.unmetRequirements("telegram", registryWith(Map.of()));
        assertThat(unmet).isEmpty();
    }

    @Test
    void provider_missing_required_sub_capability_does_not_match() {
        var consumer = manifest("consumer", "chat",
                List.of(new RequirementDeclaration("sms", List.of("receive", "send"), null)),
                List.of());
        var numbrrs = manifest("numbrrs", "mobile",
                List.of(),
                List.of(new CapabilityDeclaration("sms", List.of("receive"))));

        var resolver = new DependencyResolver(List.of(consumer, numbrrs));
        var registry = registryWith(Map.of("numbrrs", ConnectorStatus.READY));

        var unmet = resolver.unmetRequirements("consumer", registry);
        assertThat(unmet).hasSize(1);
    }

    @Test
    void email_as_bootstrap_primitive_resolves_like_sms() {
        var service = manifest("some-service", "chat",
                               List.of(new RequirementDeclaration("email", List.of("receive"), "Email verification")),
                               List.of());
        var gmail = manifest("email-google", "email",
                             List.of(),
                             List.of(new CapabilityDeclaration("email", List.of("receive", "send"))));
        var outlook = manifest("email-microsoft", "email",
                               List.of(),
                               List.of(new CapabilityDeclaration("email", List.of("receive", "send"))));

        var resolver = new DependencyResolver(List.of(service, gmail, outlook));

        // Both providers READY — requirement satisfied
        var registry = registryWith(Map.of("email-google", ConnectorStatus.READY, "email-microsoft", ConnectorStatus.READY));
        assertThat(resolver.unmetRequirements("some-service", registry)).isEmpty();

        // Neither READY — unmet, both offered as options + manual
        var emptyRegistry = registryWith(Map.of());
        var unmet         = resolver.unmetRequirements("some-service", emptyRegistry);
        assertThat(unmet).hasSize(1);
        assertThat(unmet.getFirst().capability()).isEqualTo("email");
        assertThat(unmet.getFirst().options()).hasSize(3); // gmail + outlook + manual
    }

    @Test
    void dual_bootstrap_chain_sms_and_email() {
        var service = manifest("full-service", "platform",
                               List.of(
                                       new RequirementDeclaration("sms", List.of("receive"), "SMS verification"),
                                       new RequirementDeclaration("email", List.of("receive"), "Email verification")),
                               List.of());
        var numbrrs = manifest("numbrrs", "mobile",
                               List.of(),
                               List.of(new CapabilityDeclaration("sms", List.of("receive"))));
        var gmail = manifest("email-google", "email",
                             List.of(),
                             List.of(new CapabilityDeclaration("email", List.of("receive", "send"))));

        var resolver = new DependencyResolver(List.of(service, numbrrs, gmail));

        // Both primitives available
        var registry = registryWith(Map.of("numbrrs", ConnectorStatus.READY, "email-google", ConnectorStatus.READY));
        assertThat(resolver.unmetRequirements("full-service", registry)).isEmpty();

        // Only SMS available — email unmet
        var smsOnly = registryWith(Map.of("numbrrs", ConnectorStatus.READY));
        var unmet   = resolver.unmetRequirements("full-service", smsOnly);
        assertThat(unmet).hasSize(1);
        assertThat(unmet.getFirst().capability()).isEqualTo("email");
    }


    private static ConnectorManifest manifest(String id, String platform,
            List<RequirementDeclaration> requires,
            List<CapabilityDeclaration> provides) {
        return new ConnectorManifest(new ConnectorDeclaration(
                id, platform, id + " connector", null, null,
                requires.isEmpty() ? null : requires,
                provides.isEmpty() ? null : provides,
                null, null));
    }

    private static ConnectorRegistry registryWith(Map<String, ConnectorStatus> entries) {
        var registry = new ConnectorRegistry();
        entries.forEach((id, status) ->
                registry.register(new ConnectorManifest(new ConnectorDeclaration(
                        id, "test", "", null, null, null, null, null, null)), status));
        return registry;
    }
}
