package io.casehub.connectors.lifecycle;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PersistentCredentialFacade implements ProviderCredentialFacade {

    private static final Logger LOG = Logger.getLogger(PersistentCredentialFacade.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper(
            new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

    private final Path credentialsFile;
    private final Map<String, Map<String, String>> credentials;

    public PersistentCredentialFacade(Path credentialsFile) {
        this.credentialsFile = credentialsFile;
        this.credentials = new ConcurrentHashMap<>(load(credentialsFile));
    }

    @Override
    public Optional<String> resolve(String providerId, String field) {
        var provider = credentials.get(providerId);
        return provider != null ? Optional.ofNullable(provider.get(field)) : Optional.empty();
    }

    @Override
    public Map<String, String> resolveAll(String providerId) {
        return Map.copyOf(credentials.getOrDefault(providerId, Map.of()));
    }

    @Override
    public boolean isConfigured(String providerId, List<String> requiredFields) {
        var provider = credentials.getOrDefault(providerId, Map.of());
        return requiredFields.stream().allMatch(provider::containsKey);
    }

    @Override
    public CredentialSource source(String providerId, String field) {
        return resolve(providerId, field).isPresent()
                ? CredentialSource.CREDENTIAL_STORE : CredentialSource.NONE;
    }

    @Override
    public void store(String providerId, String field, String value) {
        credentials.computeIfAbsent(providerId, k -> new ConcurrentHashMap<>()).put(field, value);
        persist();
    }

    private void persist() {
        try {
            var parent = credentialsFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            var snapshot = new LinkedHashMap<String, Map<String, String>>();
            credentials.forEach((k, v) -> snapshot.put(k, new LinkedHashMap<>(v)));
            MAPPER.writeValue(credentialsFile.toFile(), snapshot);
        } catch (IOException e) {
            LOG.warning("Failed to persist credentials to " + credentialsFile + ": " + e.getMessage());
        }
    }

    private static Map<String, Map<String, String>> load(Path path) {
        if (!Files.isReadable(path)) return Map.of();
        try {
            var raw = MAPPER.readValue(path.toFile(),
                    new TypeReference<Map<String, Map<String, String>>>() {});
            return raw != null ? raw : Map.of();
        } catch (IOException e) {
            LOG.warning("Failed to load credentials from " + path + ": " + e.getMessage());
            return Map.of();
        }
    }
}
