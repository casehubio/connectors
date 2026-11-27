package io.casehub.connectors.lifecycle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DependencyResolver {

    private final Map<String, ConnectorManifest> manifestsById;
    private final Map<String, List<ProviderEntry>> providersByCapability;
    private final Map<String, List<String>> familyMembers;
    private final ProviderCredentialFacade credentialFacade;

    private record ProviderEntry(String connectorId, List<String> sub, String setupPlaybook) {}

    public DependencyResolver(List<ConnectorManifest> manifests) {
        this(manifests, null);
    }

    public DependencyResolver(List<ConnectorManifest> manifests, ProviderCredentialFacade credentialFacade) {
        this.credentialFacade = credentialFacade;
        this.manifestsById = new HashMap<>();
        this.providersByCapability = new HashMap<>();
        this.familyMembers = new HashMap<>();
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
            var auth = decl.auth();
            if (auth != null && "oauth2".equals(auth.type()) && auth.family() != null) {
                familyMembers.computeIfAbsent(auth.family(), k -> new ArrayList<>()).add(decl.id());
            }
        }
    }

    public List<UnmetRequirement> unmetRequirements(String connectorId, ConnectorRegistry registry) {
        var manifest = manifestsById.get(connectorId);
        if (manifest == null) return List.of();

        var result = new ArrayList<UnmetRequirement>();

        if (manifest.connector().requires() != null) {
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
        }

        var auth = manifest.connector().auth();
        if (credentialFacade != null && auth != null && "oauth2".equals(auth.type()) && auth.family() != null) {
            var familyId = auth.family();
            boolean hasToken = credentialFacade.resolve(familyId, "access-token").isPresent();
            if (!hasToken) {
                var members = familyMembers.getOrDefault(familyId, List.of());
                var option = new ResolutionOption.FamilyOAuthOption(familyId, List.copyOf(members));
                result.add(new UnmetRequirement("oauth2", List.of(familyId),
                        "OAuth consent required for " + familyId + " family", List.of(option)));
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
