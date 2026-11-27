package io.casehub.connectors.lifecycle.deployment;

import io.casehub.connectors.lifecycle.ConnectorManifestLoader;
import io.casehub.connectors.lifecycle.DependencyResolver;
import io.casehub.connectors.lifecycle.ProviderCredentialFacade;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;

public class DependencyResolverCreator implements BeanCreator<DependencyResolver> {

    @Override
    public DependencyResolver create(SyntheticCreationalContext<DependencyResolver> context) {
        ProviderCredentialFacade facade = null;
        try {
            var instance = io.quarkus.arc.Arc.container().select(ProviderCredentialFacade.class);
            if (instance.isResolvable()) {
                facade = instance.get();
            }
        } catch (Exception ignored) {}
        return new DependencyResolver(ConnectorManifestLoader.discoverFromClasspath(), facade);
    }
}
