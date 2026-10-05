package io.casehub.connectors.document.ref;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.connectors.document.model.Folder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;

final class SeedLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule());

    record FileSeed(String id, String name, String folderId, String contentType,
                    int size, String owner, Instant createdAt) {}

    static List<Folder> loadFolders() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/folders.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/folders.yaml");
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<FileSeed> loadFiles() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/files.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/files.yaml");
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SeedLoader() {}
}
