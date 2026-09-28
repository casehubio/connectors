package io.casehub.connectors.document.ref;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.document.spi.DocumentPlatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefDocumentPlatformTest {

    private RefDocumentPlatform platform;
    private InMemoryDocumentBackend backend;

    @BeforeEach
    void setUp() {
        backend = new InMemoryDocumentBackend();
        platform = new RefDocumentPlatform(backend);
    }

    @Test
    void id_isRef() {
        assertThat(platform.id()).isEqualTo("ref");
    }

    @Test
    void supports_allCapabilities() {
        assertThat(platform.supports(DocumentPlatform.FileOperations.class)).isTrue();
        assertThat(platform.supports(DocumentPlatform.FolderOperations.class)).isTrue();
        assertThat(platform.supports(DocumentPlatform.SearchOperations.class)).isTrue();
        assertThat(platform.supports(DocumentPlatform.SharingOperations.class)).isTrue();
    }

    @Test
    void supports_unknownCapability_false() {
        assertThat(platform.supports(Runnable.class)).isFalse();
    }

    // --- FolderOperations ---

    @Test
    void folders_list_returnsPreloadedFolders() {
        var folders = platform.folders().list("root");
        assertThat(folders).isNotEmpty();
        assertThat(folders).extracting("name")
                .contains("Documents", "Reports");
    }

    @Test
    void folders_create_returnsNewFolder() {
        var folder = platform.folders().create("root", "New Folder");
        assertThat(folder.id()).isNotNull();
        assertThat(folder.name()).isEqualTo("New Folder");
        assertThat(folder.parentId()).isEqualTo("root");
    }

    @Test
    void folders_create_appearsInList() {
        platform.folders().create("root", "Created");
        var folders = platform.folders().list("root");
        assertThat(folders).extracting("name").contains("Created");
    }

    @Test
    void folders_list_unknownParent_returnsEmpty() {
        var folders = platform.folders().list("nonexistent");
        assertThat(folders).isEmpty();
    }

    // --- FileOperations ---

    @Test
    void files_list_returnsPreloadedFiles() {
        var folders = platform.folders().list("root");
        var docsFolder = folders.stream()
                .filter(f -> f.name().equals("Documents"))
                .findFirst().orElseThrow();
        var page = platform.files().list(docsFolder.id(), PageRequest.first(50));
        assertThat(page.items()).isNotEmpty();
    }

    @Test
    void files_list_pagination_respectsPageSize() {
        var folders = platform.folders().list("root");
        var docsFolder = folders.stream()
                .filter(f -> f.name().equals("Documents"))
                .findFirst().orElseThrow();
        var firstPage = platform.files().list(docsFolder.id(), PageRequest.first(2));
        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isNotNull();
    }

    @Test
    void files_list_pagination_cursorFetchesNextPage() {
        var folders = platform.folders().list("root");
        var docsFolder = folders.stream()
                .filter(f -> f.name().equals("Documents"))
                .findFirst().orElseThrow();
        var firstPage = platform.files().list(docsFolder.id(), PageRequest.first(2));
        var secondPage = platform.files().list(docsFolder.id(),
                new PageRequest(firstPage.nextCursor(), 2));
        assertThat(secondPage.items()).isNotEmpty();
        assertThat(secondPage.items()).extracting("id")
                .doesNotContainAnyElementsOf(
                        firstPage.items().stream().map(s -> s.id()).toList());
    }

    @Test
    void files_list_unknownFolder_returnsEmpty() {
        var page = platform.files().list("nonexistent", PageRequest.first(50));
        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void files_get_returnsMetadata() {
        var folders = platform.folders().list("root");
        var docsFolder = folders.stream()
                .filter(f -> f.name().equals("Documents"))
                .findFirst().orElseThrow();
        var files = platform.files().list(docsFolder.id(), PageRequest.first(1));
        var fileId = files.items().getFirst().id();

        var metadata = platform.files().get(fileId);

        assertThat(metadata.id()).isEqualTo(fileId);
        assertThat(metadata.name()).isNotNull();
        assertThat(metadata.contentType()).isNotNull();
        assertThat(metadata.size()).isGreaterThan(0);
    }

    @Test
    void files_get_unknown_throws() {
        assertThatThrownBy(() -> platform.files().get("nonexistent"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void files_download_returnsContent() {
        var folders = platform.folders().list("root");
        var docsFolder = folders.stream()
                .filter(f -> f.name().equals("Documents"))
                .findFirst().orElseThrow();
        var files = platform.files().list(docsFolder.id(), PageRequest.first(1));
        var fileId = files.items().getFirst().id();

        var content = platform.files().download(fileId);

        assertThat(content).isNotEmpty();
        var metadata = platform.files().get(fileId);
        assertThat(content).hasSize((int) metadata.size());
    }

    @Test
    void files_download_unknown_throws() {
        assertThatThrownBy(() -> platform.files().download("nonexistent"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void files_upload_returnsMetadata() {
        var folders = platform.folders().list("root");
        var docsFolder = folders.stream()
                .filter(f -> f.name().equals("Documents"))
                .findFirst().orElseThrow();

        byte[] content = "Hello, world!".getBytes();
        var metadata = platform.files().upload(
                docsFolder.id(), "test.txt", "text/plain", content);

        assertThat(metadata.id()).isNotNull();
        assertThat(metadata.name()).isEqualTo("test.txt");
        assertThat(metadata.contentType()).isEqualTo("text/plain");
        assertThat(metadata.size()).isEqualTo(content.length);
        assertThat(metadata.folderId()).isEqualTo(docsFolder.id());
    }

    @Test
    void files_upload_appearsInList() {
        var folders = platform.folders().list("root");
        var docsFolder = folders.stream()
                .filter(f -> f.name().equals("Documents"))
                .findFirst().orElseThrow();

        platform.files().upload(docsFolder.id(), "uploaded.pdf",
                "application/pdf", new byte[1024]);

        var page = platform.files().list(docsFolder.id(), PageRequest.first(50));
        assertThat(page.items()).extracting("name").contains("uploaded.pdf");
    }

    @Test
    void files_upload_downloadRoundTrip() {
        var folders = platform.folders().list("root");
        var docsFolder = folders.stream()
                .filter(f -> f.name().equals("Documents"))
                .findFirst().orElseThrow();

        byte[] original = "Round trip content".getBytes();
        var metadata = platform.files().upload(
                docsFolder.id(), "round.txt", "text/plain", original);

        byte[] downloaded = platform.files().download(metadata.id());
        assertThat(downloaded).isEqualTo(original);
    }

    @Test
    void files_delete_removesFile() {
        var folders = platform.folders().list("root");
        var docsFolder = folders.stream()
                .filter(f -> f.name().equals("Documents"))
                .findFirst().orElseThrow();

        var metadata = platform.files().upload(
                docsFolder.id(), "delete-me.txt", "text/plain", "temp".getBytes());

        platform.files().delete(metadata.id());

        assertThatThrownBy(() -> platform.files().get(metadata.id()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void files_delete_unknown_throws() {
        assertThatThrownBy(() -> platform.files().delete("nonexistent"))
                .isInstanceOf(NoSuchElementException.class);
    }

    // --- FolderOperations.move ---

    @Test
    void folders_move_changesParent() {
        var folders = platform.folders().list("root");
        var docsFolder = folders.stream()
                .filter(f -> f.name().equals("Documents"))
                .findFirst().orElseThrow();
        var reportsFolder = folders.stream()
                .filter(f -> f.name().equals("Reports"))
                .findFirst().orElseThrow();

        var metadata = platform.files().upload(
                docsFolder.id(), "moving.txt", "text/plain", "move me".getBytes());

        platform.folders().move(metadata.id(), reportsFolder.id());

        var docsFiles = platform.files().list(docsFolder.id(), PageRequest.first(50));
        var reportsFiles = platform.files().list(reportsFolder.id(), PageRequest.first(50));
        assertThat(docsFiles.items()).extracting("id").doesNotContain(metadata.id());
        assertThat(reportsFiles.items()).extracting("id").contains(metadata.id());
    }

    // --- SearchOperations ---

    @Test
    void search_findsMatchingFiles() {
        var page = platform.search().search("quarterly", PageRequest.first(50));
        assertThat(page.items()).isNotEmpty();
    }

    @Test
    void search_noMatch_returnsEmpty() {
        var page = platform.search().search("xyzzy-no-match-999", PageRequest.first(50));
        assertThat(page.items()).isEmpty();
    }

    // --- SharingOperations ---

    @Test
    void sharing_getShareLink_returnsLink() {
        var folders = platform.folders().list("root");
        var docsFolder = folders.stream()
                .filter(f -> f.name().equals("Documents"))
                .findFirst().orElseThrow();
        var files = platform.files().list(docsFolder.id(), PageRequest.first(1));
        var fileId = files.items().getFirst().id();

        var link = platform.sharing().getShareLink(fileId);

        assertThat(link).isNotNull();
        assertThat(link).contains(fileId);
    }

    @Test
    void sharing_getShareLink_unknown_throws() {
        assertThatThrownBy(() -> platform.sharing().getShareLink("nonexistent"))
                .isInstanceOf(NoSuchElementException.class);
    }

    // --- Isolation ---

    @Test
    void differentFolders_haveIsolatedFiles() {
        var folders = platform.folders().list("root");
        assertThat(folders.size()).isGreaterThanOrEqualTo(2);
        var first = folders.getFirst();
        var second = folders.get(1);

        var firstFiles = platform.files().list(first.id(), PageRequest.first(50));
        var secondFiles = platform.files().list(second.id(), PageRequest.first(50));

        var firstIds = firstFiles.items().stream().map(s -> s.id()).toList();
        var secondIds = secondFiles.items().stream().map(s -> s.id()).toList();
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
    }
}
