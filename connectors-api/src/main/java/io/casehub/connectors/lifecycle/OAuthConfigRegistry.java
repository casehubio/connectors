package io.casehub.connectors.lifecycle;

import java.util.Map;
import java.util.Optional;

public class OAuthConfigRegistry {

    private final Map<String, FamilyManifest.OAuthDeclaration> configs;

    public OAuthConfigRegistry(Map<String, FamilyManifest.OAuthDeclaration> configs) {
        this.configs = Map.copyOf(configs);
    }

    public Optional<FamilyManifest.OAuthDeclaration> lookup(String credentialKey) {
        return Optional.ofNullable(configs.get(credentialKey));
    }
}
