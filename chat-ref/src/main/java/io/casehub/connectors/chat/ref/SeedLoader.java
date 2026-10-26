package io.casehub.connectors.chat.ref;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        try (var is = SeedLoader.class.getResourceAsStream("/seed/channels.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/channels.yaml");
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<MemberSeed> loadMembers() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/members.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/members.yaml");
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SeedLoader() {}
}
