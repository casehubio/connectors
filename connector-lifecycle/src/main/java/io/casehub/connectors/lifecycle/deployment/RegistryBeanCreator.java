package io.casehub.connectors.lifecycle.deployment;

import io.casehub.connectors.lifecycle.ConnectorManifest;
import io.casehub.connectors.lifecycle.ConnectorManifestLoader;
import io.casehub.connectors.lifecycle.ConnectorRegistry;
import io.casehub.connectors.lifecycle.ConnectorStatus;
import io.casehub.connectors.lifecycle.ProviderCredentialFacade;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;

import java.util.List;

public class RegistryBeanCreator implements BeanCreator<ConnectorRegistry> {

    @Override
    public ConnectorRegistry create(SyntheticCreationalContext<ConnectorRegistry> context) {
        var registry = new ConnectorRegistry();
        var manifests = ConnectorManifestLoader.discoverFromClasspath();
        var facade = context.getInjectedReference(ProviderCredentialFacade.class);

        for (var manifest : manifests) {
            var decl = manifest.connector();
            var requiredFields = requiredFieldNames(decl);
            var status = facade.isConfigured(decl.id(), requiredFields)
                    ? ConnectorStatus.READY
                    : ConnectorStatus.UNCONFIGURED;
            registry.register(manifest, status);
        }
        return registry;
    }

    private static List<String> requiredFieldNames(ConnectorManifest.ConnectorDeclaration decl) {
        if (decl.auth() == null || decl.auth().fields() == null) {
            return List.of();
        }
        return decl.auth().fields().stream()
                .filter(ConnectorManifest.FieldDeclaration::isRequired)
                .map(ConnectorManifest.FieldDeclaration::name)
                .toList();
    }
}
