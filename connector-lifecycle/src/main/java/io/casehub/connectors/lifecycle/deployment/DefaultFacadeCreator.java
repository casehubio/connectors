package io.casehub.connectors.lifecycle.deployment;

import io.casehub.connectors.lifecycle.PersistentCredentialFacade;
import io.casehub.connectors.lifecycle.ProviderCredentialFacade;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;

public class DefaultFacadeCreator implements BeanCreator<ProviderCredentialFacade> {

    @Override
    public ProviderCredentialFacade create(SyntheticCreationalContext<ProviderCredentialFacade> context) {
        var home            = java.nio.file.Path.of(System.getProperty("user.home"));
        var credentialsFile = home.resolve(".casehub/credentials.yaml");
        return new PersistentCredentialFacade(credentialsFile);
    }
}
