package io.casehub.connectors.lifecycle.deployment;

import io.casehub.connectors.lifecycle.InMemoryCredentialFacade;
import io.casehub.connectors.lifecycle.ProviderCredentialFacade;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;

import java.util.Map;

public class DefaultFacadeCreator implements BeanCreator<ProviderCredentialFacade> {

    @Override
    public ProviderCredentialFacade create(SyntheticCreationalContext<ProviderCredentialFacade> context) {
        return new InMemoryCredentialFacade(Map.of());
    }
}
