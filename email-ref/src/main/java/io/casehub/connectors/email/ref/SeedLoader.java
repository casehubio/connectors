package io.casehub.connectors.email.ref;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.connectors.email.model.EmailMessage;
import io.casehub.connectors.seed.SeedDataResolver;
import io.casehub.yaml.jackson.YamlMappers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

final class SeedLoader {

    private static final ObjectMapper YAML = YamlMappers.create()
            .registerModule(new JavaTimeModule());

    static List<EmailMessage> loadMessages() {
        var maybeIs = SeedDataResolver.resolve("email", "messages.yaml");
        if (maybeIs.isEmpty()) return List.of();
        try (var is = maybeIs.get()) {
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SeedLoader() {}
}
