package io.casehub.connectors.chat.ref;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.connectors.seed.SeedDataResolver;
import io.casehub.yaml.jackson.YamlMappers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

final class SeedLoader {

    private static final ObjectMapper YAML = YamlMappers.create();

    record ChannelSeed(String name, String topic, String description,
                       boolean isPrivate, List<String> members) {}

    record MemberSeed(String id, String displayName) {}

    static List<ChannelSeed> loadChannels() {
        var maybeIs = SeedDataResolver.resolve("chat", "channels.yaml");
        if (maybeIs.isEmpty()) return List.of();
        try (var is = maybeIs.get()) {
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<MemberSeed> loadMembers() {
        var maybeIs = SeedDataResolver.resolve("chat", "members.yaml");
        if (maybeIs.isEmpty()) return List.of();
        try (var is = maybeIs.get()) {
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SeedLoader() {}
}
