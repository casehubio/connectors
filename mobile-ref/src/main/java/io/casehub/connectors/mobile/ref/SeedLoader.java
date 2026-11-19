package io.casehub.connectors.mobile.ref;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.connectors.seed.SeedDataResolver;
import io.casehub.yaml.jackson.YamlMappers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

final class SeedLoader {

    private static final ObjectMapper YAML = YamlMappers.create().registerModule(new JavaTimeModule());

    private SeedLoader() {
    }

    static List<SeedNumber> loadNumbers() {
        return load("numbers.yaml", new TypeReference<>() {});
    }

    static List<SeedConversation> loadConversations() {
        return load("conversations.yaml", new TypeReference<>() {});
    }

    static List<SeedVoicemail> loadVoicemails() {
        return load("voicemails.yaml", new TypeReference<>() {});
    }

    static List<SeedMessage> loadMessages() {
        return load("messages.yaml", new TypeReference<>() {});
    }

    private static <T> List<T> load(String filename, TypeReference<List<T>> type) {
        var maybeIs = SeedDataResolver.resolve("mobile", filename);
        if (maybeIs.isEmpty()) return List.of();
        try (var is = maybeIs.get()) {
            return YAML.readValue(is, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
