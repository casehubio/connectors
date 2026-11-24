package io.casehub.connectors.lifecycle;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProviderCredentialFacade {

    Optional<String> resolve(String providerId, String field);

    Map<String, String> resolveAll(String providerId);

    boolean isConfigured(String providerId, List<String> requiredFields);

    CredentialSource source(String providerId, String field);

    enum CredentialSource { CREDENTIAL_STORE, CONFIG, ENVIRONMENT, SERVICE_CONNECTION, NONE }
}
