package io.casehub.connectors.lifecycle;

import java.util.Map;
import java.util.Optional;

public class OAuthConfigRegistry {

    public record OAuthConfig(FamilyManifest.OAuthDeclaration oauth, FamilyManifest.ConsentStrategy consent) {}

    private final Map<String, OAuthConfig> configs;

    public OAuthConfigRegistry(Map<String, OAuthConfig> configs) {
        this.configs = Map.copyOf(configs);
    }

    public Optional<FamilyManifest.OAuthDeclaration> lookup(String credentialKey) {
        var config = configs.get(credentialKey);
        return config != null ? Optional.of(config.oauth()) : Optional.empty();
    }

    public Optional<OAuthConfig> lookupConfig(String credentialKey) {
        return Optional.ofNullable(configs.get(credentialKey));
    }
}
