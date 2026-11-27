package io.casehub.connectors.lifecycle.deployment;

import io.casehub.connectors.lifecycle.ConnectorManifestLoader;
import io.casehub.connectors.lifecycle.FamilyManifest;
import io.casehub.connectors.lifecycle.OAuthConfigRegistry;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;

import java.util.HashMap;

public class OAuthConfigRegistryCreator implements BeanCreator<OAuthConfigRegistry> {

    @Override
    public OAuthConfigRegistry create(SyntheticCreationalContext<OAuthConfigRegistry> context) {
        var manifests = ConnectorManifestLoader.discoverFromClasspath();
        var configs = new HashMap<String, FamilyManifest.OAuthDeclaration>();
        for (var m : manifests) {
            var auth = m.connector().auth();
            if (auth != null && auth.oauth() != null) {
                configs.putIfAbsent(m.connector().credentialKey(), auth.oauth());
            }
        }
        return new OAuthConfigRegistry(configs);
    }
}
