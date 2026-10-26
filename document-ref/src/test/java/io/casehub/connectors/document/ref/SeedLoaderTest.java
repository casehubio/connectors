package io.casehub.connectors.document.ref;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedLoaderTest {

    @Test
    void loadsFiveFolders() {
        var folders = SeedLoader.loadFolders();
        assertThat(folders).hasSize(5);
        assertThat(folders).extracting("name")
                           .containsExactly("Family Documents", "Financial", "Health & Care",
                                            "Home & Maintenance", "Travel");
    }

    @Test
    void loadsSeventeenFiles() {
        var files = SeedLoader.loadFiles();
        assertThat(files).hasSize(17);
    }

    @Test
    void fileHasCorrectFields() {
        var files = SeedLoader.loadFiles();
        var doc = files.stream()
                       .filter(f -> f.id().equals("doc-001"))
                       .findFirst().orElseThrow();
        assertThat(doc.name()).contains("passport");
        assertThat(doc.folderId()).isEqualTo("folder-family");
        assertThat(doc.owner()).isEqualTo("jonny.alpha@meridian-eng.co.uk");
    }
}
