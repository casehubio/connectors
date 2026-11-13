package io.casehub.connectors.document.microsoft;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.document.spi.DocumentPlatform;
import io.casehub.connectors.microsoft.MicrosoftGraphClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MicrosoftDrivePlatformTest {

    private MicrosoftDrivePlatform platform;

    @BeforeEach
    void setUp() {
        var graph = new MicrosoftGraphClient(request -> {
            String uri = request.uri().toString();
            String method = request.method();

            // List children (files+folders — top-level children query with $top)
            if (uri.contains("/children") && uri.contains("$top") && method.equals("GET")) {
                return new StubHttpResponse(200, """
                        {"value":[%s]}""".formatted(fileItemJson("file-1", "report.pdf",
                        "root", "application/pdf", 1024)));
            }

            // Get item
            if (uri.matches(".*\\/items\\/[^/]+$") && method.equals("GET")
                    && !uri.contains("children") && !uri.contains("content")
                    && !uri.contains("search") && !uri.contains("createLink")
                    && !uri.contains("delta")) {
                return new StubHttpResponse(200,
                        fileItemJson("file-1", "report.pdf", "root",
                                "application/pdf", 1024));
            }

            // Upload (simple PUT via send())
            if (uri.contains(":/content") && method.equals("PUT")) {
                return new StubHttpResponse(201,
                        fileItemJson("file-new", "uploaded.txt", "folder-1",
                                "text/plain", 256));
            }

            // Delete
            if (method.equals("DELETE")) {
                return new StubHttpResponse(204, "");
            }

            // Delta sync
            if (uri.contains("/delta")) {
                return new StubHttpResponse(200, """
                        {"value":[%s,{"id":"del-1","@removed":{"reason":"deleted"}}],\
                        "@odata.deltaLink":"https://graph.microsoft.com/v1.0/delta?token=xyz"}"""
                        .formatted(fileItemJson("file-1", "report.pdf", "root",
                                "application/pdf", 1024)));
            }

            // List folders (children without file filter — returns folder items)
            if (uri.contains("/children") && method.equals("GET")) {
                return new StubHttpResponse(200, """
                        {"value":[%s]}""".formatted(folderItemJson("folder-1",
                        "Documents", "root")));
            }

            // Create folder
            if (uri.contains("/children") && method.equals("POST")) {
                return new StubHttpResponse(201,
                        folderItemJson("folder-new", "New Folder", "root"));
            }

            // Move (PATCH)
            if (method.equals("PATCH")) {
                return new StubHttpResponse(200, "{}");
            }

            // Search
            if (uri.contains("search(q=")) {
                return new StubHttpResponse(200, """
                        {"value":[%s]}""".formatted(fileItemJson("file-1", "report.pdf",
                        "root", "application/pdf", 1024)));
            }

            // Create share link
            if (uri.contains("createLink") && method.equals("POST")) {
                return new StubHttpResponse(200, """
                        {"link":{"webUrl":"https://1drv.ms/b/s!shared-link"}}""");
            }

            return new StubHttpResponse(200, """
                    {"value":[]}""");
        });

        platform = new MicrosoftDrivePlatform(
                new MicrosoftDriveClient(graph, "/me/drive"), "microsoft-onedrive");
    }

    @Test
    void idReturnsConfiguredValue() {
        assertThat(platform.id()).isEqualTo("microsoft-onedrive");
    }

    @Test
    void supportsAllCapabilities() {
        assertThat(platform.supports(DocumentPlatform.FileOperations.class)).isTrue();
        assertThat(platform.supports(DocumentPlatform.FolderOperations.class)).isTrue();
        assertThat(platform.supports(DocumentPlatform.SearchOperations.class)).isTrue();
        assertThat(platform.supports(DocumentPlatform.SharingOperations.class)).isTrue();
    }

    @Test
    void degradedPlatformSupportsNothing() {
        var degraded = new MicrosoftDrivePlatform(null, "microsoft-sharepoint");
        assertThat(degraded.supports(DocumentPlatform.FileOperations.class)).isFalse();
        assertThatThrownBy(degraded::files)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Nested
    class FileOperationsTest {

        @Test
        void listReturnsFiles() {
            var page = platform.files().list("root", PageRequest.first(25));
            assertThat(page.items()).hasSize(1);
            assertThat(page.items().get(0).name()).isEqualTo("report.pdf");
            assertThat(page.items().get(0).contentType()).isEqualTo("application/pdf");
        }

        @Test
        void getReturnsMetadata() {
            var meta = platform.files().get("file-1");
            assertThat(meta.id()).isEqualTo("file-1");
            assertThat(meta.name()).isEqualTo("report.pdf");
            assertThat(meta.owner()).isEqualTo("Alice");
        }

        @Test
        void uploadReturnsMetadata() {
            var meta = platform.files().upload("folder-1", "uploaded.txt",
                    "text/plain", "hello".getBytes());
            assertThat(meta.id()).isEqualTo("file-new");
            assertThat(meta.name()).isEqualTo("uploaded.txt");
        }

        @Test
        void deleteDoesNotThrow() {
            assertThatCode(() -> platform.files().delete("file-1"))
                    .doesNotThrowAnyException();
        }

        @Test
        void listSyncReturnsDeltaResult() {
            var result = platform.files().listSync(SyncRequest.initial(50));
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).id()).isEqualTo("file-1");
            assertThat(result.deletedIds()).containsExactly("del-1");
            assertThat(result.syncToken()).contains("delta?token=xyz");
        }
    }

    @Nested
    class FolderOperationsTest {

        @Test
        void listReturnsFolders() {
            var folders = platform.folders().list("root");
            assertThat(folders).hasSize(1);
            assertThat(folders.get(0).name()).isEqualTo("Documents");
        }

        @Test
        void createReturnsFolder() {
            var folder = platform.folders().create("root", "New Folder");
            assertThat(folder.id()).isEqualTo("folder-new");
            assertThat(folder.name()).isEqualTo("New Folder");
        }

        @Test
        void moveDoesNotThrow() {
            assertThatCode(() -> platform.folders().move("file-1", "folder-1"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class SearchOperationsTest {

        @Test
        void searchReturnsResults() {
            var page = platform.search().search("report", PageRequest.first(25));
            assertThat(page.items()).hasSize(1);
            assertThat(page.items().get(0).name()).isEqualTo("report.pdf");
        }
    }

    @Nested
    class SharingOperationsTest {

        @Test
        void getShareLinkReturnsUrl() {
            var link = platform.sharing().getShareLink("file-1");
            assertThat(link).isEqualTo("https://1drv.ms/b/s!shared-link");
        }
    }

    @Test
    void validateCdnHostAcceptsAllowedHosts() {
        assertThatCode(() -> MicrosoftDriveClient.validateCdnHost(
                "https://my-tenant.sharepoint.com/download/file"))
                .doesNotThrowAnyException();
        assertThatCode(() -> MicrosoftDriveClient.validateCdnHost(
                "https://abc.1drv.com/file"))
                .doesNotThrowAnyException();
        assertThatCode(() -> MicrosoftDriveClient.validateCdnHost(
                "https://storage.blob.core.windows.net/container/blob"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateCdnHostRejectsUnknownHosts() {
        assertThatThrownBy(() -> MicrosoftDriveClient.validateCdnHost(
                "https://evil.example.com/steal"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("untrusted host");
    }

    private static String fileItemJson(String id, String name, String parentId,
            String mimeType, long size) {
        return """
                {"id":"%s","name":"%s","size":%d,\
                "file":{"mimeType":"%s"},\
                "parentReference":{"id":"%s"},\
                "createdBy":{"user":{"displayName":"Alice"}},\
                "webUrl":"https://onedrive.live.com/view/%s",\
                "createdDateTime":"2026-01-01T00:00:00Z",\
                "lastModifiedDateTime":"2026-06-15T12:00:00Z"}"""
                .formatted(id, name, size, mimeType, parentId, name);
    }

    private static String folderItemJson(String id, String name, String parentId) {
        return """
                {"id":"%s","name":"%s","size":0,\
                "folder":{"childCount":3},\
                "parentReference":{"id":"%s"},\
                "createdDateTime":"2026-01-01T00:00:00Z"}"""
                .formatted(id, name, parentId);
    }
}
