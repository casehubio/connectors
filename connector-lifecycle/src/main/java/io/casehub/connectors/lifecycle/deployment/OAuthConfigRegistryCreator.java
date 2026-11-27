package io.casehub.connectors.lifecycle.deployment;

import io.casehub.connectors.lifecycle.ConnectorManifestLoader;
import io.casehub.connectors.lifecycle.FamilyManifest;
import io.casehub.connectors.lifecycle.FamilyManifestLoader;
import io.casehub.connectors.lifecycle.OAuthConfigRegistry;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;

import java.util.HashMap;

public class OAuthConfigRegistryCreator implements BeanCreator<OAuthConfigRegistry> {

    @Override
    public OAuthConfigRegistry create(SyntheticCreationalContext<OAuthConfigRegistry> context) {
        var manifests = ConnectorManifestLoader.discoverFromClasspath();
        var families = FamilyManifestLoader.discover();
        var configs = new HashMap<String, OAuthConfigRegistry.OAuthConfig>();
        for (var m : manifests) {
            var auth = m.connector().auth();
            if (auth != null && auth.oauth() != null) {
                var consent = FamilyManifest.ConsentStrategy.AGGREGATED;
                if (auth.family() != null) {
                    var family = families.get(auth.family());
                    if (family != null && family.family().consent() != null) {
                        consent = family.family().consent();
                    }
                }
                var flows = auth.family() != null && families.containsKey(auth.family())
                        ? families.get(auth.family()).family().flows() : null;
                configs.putIfAbsent(m.connector().credentialKey(),
                        new OAuthConfigRegistry.OAuthConfig(auth.oauth(), consent, flows));
            }
        }
        return new OAuthConfigRegistry(configs);
    }
}
