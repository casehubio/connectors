package io.casehub.connectors.lifecycle.deployment;

import io.casehub.connectors.lifecycle.ConnectorManifestLoader;
import io.casehub.connectors.lifecycle.DependencyResolver;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;

public class DependencyResolverCreator implements BeanCreator<DependencyResolver> {

    @Override
    public DependencyResolver create(SyntheticCreationalContext<DependencyResolver> context) {
        return new DependencyResolver(ConnectorManifestLoader.discoverFromClasspath());
    }
}
