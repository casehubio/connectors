package io.casehub.connectors.document.ref;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedLoaderTest {

    @Test
    void loadsThreeFolders() {
        var folders = SeedLoader.loadFolders();
        assertThat(folders).hasSize(3);
        assertThat(folders).extracting("name")
                .containsExactly("Documents", "Reports", "Archive");
    }

    @Test
    void loadsSevenFiles() {
        var files = SeedLoader.loadFiles();
        assertThat(files).hasSize(7);
    }

    @Test
    void fileHasCorrectFields() {
        var files = SeedLoader.loadFiles();
        var doc = files.stream()
                .filter(f -> f.id().equals("doc-001"))
                .findFirst().orElseThrow();
        assertThat(doc.name()).isEqualTo("Q3 Quarterly Plan.docx");
        assertThat(doc.folderId()).isEqualTo("folder-docs");
        assertThat(doc.size()).isEqualTo(2048);
        assertThat(doc.owner()).isEqualTo("user@example.com");
    }
}
