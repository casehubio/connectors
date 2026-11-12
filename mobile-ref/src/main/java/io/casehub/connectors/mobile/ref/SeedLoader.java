package io.casehub.connectors.mobile.ref;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.yaml.jackson.YamlMappers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

final class SeedLoader {

    private static final ObjectMapper YAML = YamlMappers.create().registerModule(new JavaTimeModule());

    private SeedLoader() {
    }

    static List<SeedNumber> loadNumbers() {
        return load("/seed/numbers.yaml", new TypeReference<>() {});
    }

    static List<SeedConversation> loadConversations() {
        return load("/seed/conversations.yaml", new TypeReference<>() {});
    }

    static List<SeedVoicemail> loadVoicemails() {
        return load("/seed/voicemails.yaml", new TypeReference<>() {});
    }

    static List<SeedMessage> loadMessages() {
        return load("/seed/messages.yaml", new TypeReference<>() {});
    }

    private static <T> List<T> load(String resource, TypeReference<List<T>> type) {
        try (var is = SeedLoader.class.getResourceAsStream(resource)) {
            if (is == null) {
                throw new IllegalStateException("Seed data not found: " + resource);
            }
            return YAML.readValue(is, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
