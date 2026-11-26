package io.casehub.connectors.steps;

import io.casehub.connectors.lifecycle.ConnectorRegistry;
import io.casehub.connectors.lifecycle.ConnectorStatus;
import io.casehub.connectors.lifecycle.DependencyResolver;
import io.casehub.connectors.lifecycle.ResolutionOption;
import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.Result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Plugin(value = "require-capability", description = "Require a capability from the ecosystem — fails with resolution options if unmet", absorbSiblings = true)
public record RequireCapabilityPlugin(@Required String capability, List<String> sub) {

    @Execute
    public Result execute(DependencyResolver resolver, ConnectorRegistry registry) {
        var effectiveSub = sub != null ? sub : List.<String>of();
        var options = resolver.resolutionOptions(capability, effectiveSub, registry);

        var readyProvider = options.stream()
                .filter(o -> o instanceof ResolutionOption.ProviderOption p && p.status() == ConnectorStatus.READY)
                .map(o -> (ResolutionOption.ProviderOption) o)
                .findFirst();

        if (readyProvider.isPresent()) {
            var output = new LinkedHashMap<String, Object>();
            output.put("capability", capability);
            output.put("satisfied", true);
            output.put("provider", readyProvider.get().connectorId());
            return Result.of(output);
        }

        var optionsList = new ArrayList<Map<String, Object>>();
        for (var option : options) {
            var entry = new LinkedHashMap<String, Object>();
            switch (option) {
                case ResolutionOption.ProviderOption p -> {
                    entry.put("type", "provider");
                    entry.put("connector-id", p.connectorId());
                    entry.put("status", p.status().name());
                    if (p.setupPlaybook() != null) entry.put("setup-playbook", p.setupPlaybook());
                }
                case ResolutionOption.ManualOption m -> {
                    entry.put("type", "manual");
                    entry.put("capability", m.capability());
                }
            }
            optionsList.add(entry);
        }

        return Result.failed(
                "Capability '" + capability + "' is not satisfied by any READY provider",
                "unmet-requirement",
                Map.of("capability", capability, "sub", effectiveSub, "options", optionsList));
    }
}
