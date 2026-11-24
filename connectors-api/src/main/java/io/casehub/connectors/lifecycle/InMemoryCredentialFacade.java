package io.casehub.connectors.lifecycle;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryCredentialFacade implements ProviderCredentialFacade {

    private final Map<String, Map<String, String>> credentials;

    public InMemoryCredentialFacade(Map<String, Map<String, String>> credentials) {
        this.credentials = Map.copyOf(credentials);
    }

    @Override
    public Optional<String> resolve(String providerId, String field) {
        return Optional.ofNullable(credentials.getOrDefault(providerId, Map.of()).get(field));
    }

    @Override
    public Map<String, String> resolveAll(String providerId) {
        return credentials.getOrDefault(providerId, Map.of());
    }

    @Override
    public boolean isConfigured(String providerId, List<String> requiredFields) {
        var providerCreds = credentials.getOrDefault(providerId, Map.of());
        return requiredFields.stream().allMatch(providerCreds::containsKey);
    }

    @Override
    public CredentialSource source(String providerId, String field) {
        return resolve(providerId, field).isPresent() ? CredentialSource.CREDENTIAL_STORE : CredentialSource.NONE;
    }
}
