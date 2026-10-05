package io.casehub.connectors.contacts.ref;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.connectors.contacts.model.Group;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

final class SeedLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule());

    record ContactSeed(String displayName, String givenName, String familyName,
                       String email, String phone, String company, String title,
                       List<String> groups) {}

    static List<Group> loadGroups() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/groups.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/groups.yaml");
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<ContactSeed> loadContacts() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/contacts.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/contacts.yaml");
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SeedLoader() {}
}
