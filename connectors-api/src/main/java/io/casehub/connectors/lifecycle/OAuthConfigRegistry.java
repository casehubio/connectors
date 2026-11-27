package io.casehub.connectors.lifecycle;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OAuthConfigRegistry {

    public record OAuthConfig(FamilyManifest.OAuthDeclaration oauth, FamilyManifest.ConsentStrategy consent,
                               Map<String, FamilyManifest.FlowDeclaration> flows) {

        public OAuthConfig(FamilyManifest.OAuthDeclaration oauth, FamilyManifest.ConsentStrategy consent) {
            this(oauth, consent, null);
        }

        public List<String> delegatedScopes() {
            if (flows != null && flows.containsKey("delegated")) {
                return flows.get("delegated").scopes();
            }
            return oauth.scopes() != null ? oauth.scopes() : List.of();
        }
    }

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
