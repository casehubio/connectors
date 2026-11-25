package io.casehub.connectors.lifecycle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DependencyResolver {

    private final Map<String, ConnectorManifest> manifestsById;
    private final Map<String, List<ProviderEntry>> providersByCapability;

    private record ProviderEntry(String connectorId, List<String> sub, String setupPlaybook) {}

    public DependencyResolver(List<ConnectorManifest> manifests) {
        this.manifestsById = new HashMap<>();
        this.providersByCapability = new HashMap<>();
        for (var m : manifests) {
            var decl = m.connector();
            manifestsById.put(decl.id(), m);
            if (decl.provides() != null) {
                for (var cap : decl.provides()) {
                    providersByCapability
                            .computeIfAbsent(cap.capability(), k -> new ArrayList<>())
                            .add(new ProviderEntry(decl.id(), cap.sub(), decl.setupPlaybook()));
                }
            }
        }
    }

    public List<UnmetRequirement> unmetRequirements(String connectorId, ConnectorRegistry registry) {
        var manifest = manifestsById.get(connectorId);
        if (manifest == null || manifest.connector().requires() == null) return List.of();

        var result = new ArrayList<UnmetRequirement>();
        for (var req : manifest.connector().requires()) {
            var options = resolutionOptions(req.capability(), req.sub(), registry);
            boolean satisfied = options.stream()
                    .filter(o -> o instanceof ResolutionOption.ProviderOption)
                    .map(o -> (ResolutionOption.ProviderOption) o)
                    .anyMatch(p -> p.status() == ConnectorStatus.READY);
            if (!satisfied) {
                result.add(new UnmetRequirement(req.capability(), req.sub(), req.reason(), options));
            }
        }
        return List.copyOf(result);
    }

    public List<ResolutionOption> resolutionOptions(String capability, List<String> sub, ConnectorRegistry registry) {
        var options = new ArrayList<ResolutionOption>();
        var providers = providersByCapability.getOrDefault(capability, List.of());
        for (var entry : providers) {
            if (matches(entry.sub(), sub)) {
                var status = registry.status(entry.connectorId());
                options.add(new ResolutionOption.ProviderOption(
                        entry.connectorId(),
                        status != null ? status : ConnectorStatus.UNCONFIGURED,
                        entry.setupPlaybook()));
            }
        }
        options.add(new ResolutionOption.ManualOption(capability));
        return List.copyOf(options);
    }

    // Unqualified (empty sub) means full capability — satisfies any qualified requirement
    private static boolean matches(List<String> providerSub, List<String> requiredSub) {
        if (requiredSub == null || requiredSub.isEmpty()) return true;
        if (providerSub == null || providerSub.isEmpty()) return true;
        return providerSub.containsAll(requiredSub);
    }
}
