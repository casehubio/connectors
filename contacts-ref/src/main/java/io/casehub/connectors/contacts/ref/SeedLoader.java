package io.casehub.connectors.contacts.ref;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.connectors.contacts.model.Group;
import io.casehub.connectors.seed.SeedDataResolver;
import io.casehub.yaml.jackson.YamlMappers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

final class SeedLoader {

    private static final ObjectMapper YAML = YamlMappers.create()
            .registerModule(new JavaTimeModule());

    record ContactSeed(String displayName, String givenName, String familyName,
                       String email, String phone, String company, String title,
                       List<String> groups) {}

    static List<Group> loadGroups() {
        var maybeIs = SeedDataResolver.resolve("contacts", "groups.yaml");
        if (maybeIs.isEmpty()) return List.of();
        try (var is = maybeIs.get()) {
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<ContactSeed> loadContacts() {
        var maybeIs = SeedDataResolver.resolve("contacts", "contacts.yaml");
        if (maybeIs.isEmpty()) return List.of();
        try (var is = maybeIs.get()) {
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SeedLoader() {}
}
