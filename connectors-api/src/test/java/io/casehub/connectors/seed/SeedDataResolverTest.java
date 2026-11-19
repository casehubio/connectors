package io.casehub.connectors.seed;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SeedDataResolverTest {

    @AfterEach
    void resetSeedDir() {
        SeedDataResolver.setSeedDir(null);
    }

    @Test
    void resolve_returnsEmpty_whenNoSeedDirAndNoClasspath() {
        assertThat(SeedDataResolver.resolve("calendar", "nonexistent.yaml")).isEmpty();
    }

    @Test
    void resolve_returnsClasspathResource_whenNoSeedDir() {
        var result = SeedDataResolver.resolve("test", "test-data.yaml");
        assertThat(result).isPresent();
    }

    @Test
    void resolve_returnsFileBasedResource_whenSeedDirSet(@TempDir Path tempDir) throws IOException {
        Path domainDir = tempDir.resolve("calendar");
        Files.createDirectories(domainDir);
        Files.writeString(domainDir.resolve("events.yaml"), "- name: test");

        SeedDataResolver.setSeedDir(tempDir);

        var result = SeedDataResolver.resolve("calendar", "events.yaml");
        assertThat(result).isPresent();
        try (var is = result.get()) {
            assertThat(new String(is.readAllBytes())).contains("test");
        }
    }

    @Test
    void resolve_fileBasedTakesPrecedenceOverClasspath(@TempDir Path tempDir) throws IOException {
        Path domainDir = tempDir.resolve("test");
        Files.createDirectories(domainDir);
        Files.writeString(domainDir.resolve("test-data.yaml"), "file-based-content");

        SeedDataResolver.setSeedDir(tempDir);

        var result = SeedDataResolver.resolve("test", "test-data.yaml");
        assertThat(result).isPresent();
        try (var is = result.get()) {
            assertThat(new String(is.readAllBytes())).isEqualTo("file-based-content");
        }
    }

    @Test
    void resolve_fallsBackToClasspath_whenFileNotInSeedDir(@TempDir Path tempDir) {
        SeedDataResolver.setSeedDir(tempDir);
        var result = SeedDataResolver.resolve("test", "test-data.yaml");
        assertThat(result).isPresent();
    }

    @Test
    void resolve_returnsEmpty_whenSeedDirSetButNoFileAndNoClasspath(@TempDir Path tempDir) {
        SeedDataResolver.setSeedDir(tempDir);
        assertThat(SeedDataResolver.resolve("calendar", "nonexistent.yaml")).isEmpty();
    }
}
