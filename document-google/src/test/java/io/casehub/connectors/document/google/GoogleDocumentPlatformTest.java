package io.casehub.connectors.document.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.document.spi.DocumentPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleDocumentPlatformTest {

    private WireMockServer wireMock;
    private GoogleDocumentPlatform platform;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        Drive driveService = new Drive.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                request -> {})
                .setApplicationName("test")
                .setRootUrl("http://localhost:" + wireMock.port() + "/")
                .build();

        platform = new GoogleDocumentPlatform(driveService);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void id_isGoogle() {
        assertThat(platform.id()).isEqualTo("google");
    }

    @Test
    void supports_allCapabilities() {
        assertThat(platform.supports(DocumentPlatform.FileOperations.class)).isTrue();
        assertThat(platform.supports(DocumentPlatform.FolderOperations.class)).isTrue();
        assertThat(platform.supports(DocumentPlatform.SearchOperations.class)).isTrue();
        assertThat(platform.supports(DocumentPlatform.SharingOperations.class)).isTrue();
    }

    @Test
    void isActive_withClient_true() {
        assertThat(platform.isActive()).isTrue();
    }

    @Test
    void isActive_withoutClient_false() {
        var unconfigured = new GoogleDocumentPlatform("", "", "");
        assertThat(unconfigured.isActive()).isFalse();
    }

    @Test
    void requireClient_noClient_throwsIllegalState() {
        var unconfigured = new GoogleDocumentPlatform("", "", "");
        assertThatThrownBy(() -> unconfigured.files().list("root", PageRequest.first(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not initialised");
    }

    // --- FileOperations ---

    @Test
    void files_list_returnsMappedFiles() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "files": [
                                    {
                                      "id": "file-1",
                                      "name": "Report.pdf",
                                      "mimeType": "application/pdf",
                                      "size": "4096",
                                      "parents": ["folder-1"],
                                      "createdTime": "2026-09-15T10:00:00.000Z",
                                      "modifiedTime": "2026-09-16T14:00:00.000Z"
                                    }
                                  ]
                                }
                                """)));

        var page = platform.files().list("folder-1", PageRequest.first(10));

        assertThat(page.items()).hasSize(1);
        var file = page.items().getFirst();
        assertThat(file.id()).isEqualTo("file-1");
        assertThat(file.name()).isEqualTo("Report.pdf");
        assertThat(file.contentType()).isEqualTo("application/pdf");
        assertThat(file.size()).isEqualTo(4096);
        assertThat(file.folderId()).isEqualTo("folder-1");
    }

    @Test
    void files_list_pagination() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "files": [{"id": "f1", "name": "A.txt", "mimeType": "text/plain"}],
                                  "nextPageToken": "page2-token"
                                }
                                """)));

        var page = platform.files().list("root", PageRequest.first(1));

        assertThat(page.items()).hasSize(1);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("page2-token");
    }

    @Test
    void files_get_returnsMetadata() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files/file-1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "file-1",
                                  "name": "Design.md",
                                  "mimeType": "text/markdown",
                                  "size": "512",
                                  "parents": ["folder-docs"],
                                  "owners": [{"emailAddress": "alice@example.com"}],
                                  "webViewLink": "https://drive.google.com/file/d/file-1/view",
                                  "createdTime": "2026-09-15T10:00:00.000Z",
                                  "modifiedTime": "2026-09-15T10:00:00.000Z"
                                }
                                """)));

        var metadata = platform.files().get("file-1");

        assertThat(metadata.id()).isEqualTo("file-1");
        assertThat(metadata.name()).isEqualTo("Design.md");
        assertThat(metadata.owner()).isEqualTo("alice@example.com");
        assertThat(metadata.webViewLink()).contains("file-1");
    }

    @Test
    void files_get_notFound_throws() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files/nonexistent"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error": {"code": 404, "message": "File not found"}}
                                """)));

        assertThatThrownBy(() -> platform.files().get("nonexistent"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void files_upload_returnsMetadata() {
        wireMock.stubFor(post(urlPathEqualTo("/upload/drive/v3/files"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "new-file",
                                  "name": "uploaded.txt",
                                  "mimeType": "text/plain",
                                  "size": "13",
                                  "parents": ["folder-1"],
                                  "createdTime": "2026-09-28T10:00:00.000Z",
                                  "modifiedTime": "2026-09-28T10:00:00.000Z"
                                }
                                """)));

        var metadata = platform.files().upload(
                "folder-1", "uploaded.txt", "text/plain", "Hello, world!".getBytes());

        assertThat(metadata.id()).isEqualTo("new-file");
        assertThat(metadata.name()).isEqualTo("uploaded.txt");
    }

    @Test
    void files_delete_callsDeleteEndpoint() {
        wireMock.stubFor(delete(urlPathEqualTo("/drive/v3/files/file-1"))
                .willReturn(aResponse().withStatus(204)));

        platform.files().delete("file-1");

        wireMock.verify(1, WireMock.deleteRequestedFor(
                urlPathEqualTo("/drive/v3/files/file-1")));
    }

    @Test
    void files_delete_notFound_throws() {
        wireMock.stubFor(delete(urlPathEqualTo("/drive/v3/files/nonexistent"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error": {"code": 404, "message": "File not found"}}
                                """)));

        assertThatThrownBy(() -> platform.files().delete("nonexistent"))
                .isInstanceOf(NoSuchElementException.class);
    }

    // --- FolderOperations ---

    @Test
    void folders_list_returnsFolders() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "files": [
                                    {"id": "folder-docs", "name": "Documents", "parents": ["root"], "createdTime": "2026-09-01T00:00:00.000Z"},
                                    {"id": "folder-reports", "name": "Reports", "parents": ["root"], "createdTime": "2026-09-01T00:00:00.000Z"}
                                  ]
                                }
                                """)));

        var folders = platform.folders().list("root");

        assertThat(folders).hasSize(2);
        assertThat(folders).extracting("name").contains("Documents", "Reports");
    }

    @Test
    void folders_create_returnsNewFolder() {
        wireMock.stubFor(post(urlPathEqualTo("/drive/v3/files"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "new-folder",
                                  "name": "New Folder",
                                  "parents": ["root"],
                                  "createdTime": "2026-09-28T10:00:00.000Z"
                                }
                                """)));

        var folder = platform.folders().create("root", "New Folder");

        assertThat(folder.id()).isEqualTo("new-folder");
        assertThat(folder.name()).isEqualTo("New Folder");
        assertThat(folder.parentId()).isEqualTo("root");
    }

    // --- SearchOperations ---

    @Test
    void search_returnsMatchingFiles() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "files": [
                                    {"id": "match-1", "name": "Quarterly Report.pdf", "mimeType": "application/pdf", "size": "2048"}
                                  ]
                                }
                                """)));

        var page = platform.search().search("quarterly", PageRequest.first(10));

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().name()).contains("Quarterly");
    }

    @Test
    void search_noResults_returnsEmpty() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"files": []}
                                """)));

        var page = platform.search().search("xyzzy", PageRequest.first(10));

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }

    // --- SharingOperations ---

    @Test
    void sharing_getShareLink_returnsLink() {
        wireMock.stubFor(post(urlPathEqualTo("/drive/v3/files/file-1/permissions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id": "perm-1", "type": "anyone", "role": "reader"}
                                """)));
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files/file-1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id": "file-1", "webViewLink": "https://drive.google.com/file/d/file-1/view?usp=sharing"}
                                """)));

        var link = platform.sharing().getShareLink("file-1");

        assertThat(link).contains("file-1");
        assertThat(link).contains("sharing");
    }

    @Test
    void sharing_getShareLink_notFound_throws() {
        wireMock.stubFor(post(urlPathEqualTo("/drive/v3/files/nonexistent/permissions"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error": {"code": 404, "message": "File not found"}}
                                """)));

        assertThatThrownBy(() -> platform.sharing().getShareLink("nonexistent"))
                .isInstanceOf(NoSuchElementException.class);
    }
// --- Sync ---

    @Test
    void listSync_initialSync_returnsFilesWithToken() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/changes/startPageToken"))
                                 .willReturn(aResponse()
                                                     .withHeader("Content-Type", "application/json")
                                                     .withBody("""
                                                               {"startPageToken": "page-token-1"}
                                                               """)));

        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files"))
                                 .willReturn(aResponse()
                                                     .withHeader("Content-Type", "application/json")
                                                     .withBody("""
                                                               {
                                                                 "files": [
                                                                   {
                                                                     "id": "f-1", "name": "Doc.txt",
                                                                     "mimeType": "text/plain", "size": "100",
                                                                     "parents": ["folder-1"],
                                                                     "createdTime": "2026-09-15T10:00:00.000Z",
                                                                     "modifiedTime": "2026-09-15T10:00:00.000Z"
                                                                   }
                                                                 ]
                                                               }
                                                               """)));

        var result = platform.files().listSync(io.casehub.connectors.SyncRequest.initial(100));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().name()).isEqualTo("Doc.txt");
        assertThat(result.syncToken()).isEqualTo("page-token-1");
        assertThat(result.deletedIds()).isEmpty();
    }

    @Test
    void listSync_incrementalSync_returnsChangesAndDeletes() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/changes"))
                                 .withQueryParam("pageToken", WireMock.equalTo("page-token-1"))
                                 .willReturn(aResponse()
                                                     .withHeader("Content-Type", "application/json")
                                                     .withBody("""
                                                               {
                                                                 "changes": [
                                                                   {
                                                                     "fileId": "f-2",
                                                                     "removed": false,
                                                                     "file": {
                                                                       "id": "f-2", "name": "NewDoc.txt",
                                                                       "mimeType": "text/plain", "size": "200",
                                                                       "parents": ["folder-1"],
                                                                       "createdTime": "2026-09-16T10:00:00.000Z",
                                                                       "modifiedTime": "2026-09-16T10:00:00.000Z"
                                                                     }
                                                                   },
                                                                   {
                                                                     "fileId": "f-old",
                                                                     "removed": true
                                                                   }
                                                                 ],
                                                                 "newStartPageToken": "page-token-2"
                                                               }
                                                               """)));

        var result = platform.files().listSync(new io.casehub.connectors.SyncRequest("page-token-1", 100));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().name()).isEqualTo("NewDoc.txt");
        assertThat(result.deletedIds()).containsExactly("f-old");
        assertThat(result.syncToken()).isEqualTo("page-token-2");
    }

    @Test
    void listSync_expiredToken_throwsSyncTokenExpired() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/changes"))
                                 .withQueryParam("pageToken", WireMock.equalTo("expired"))
                                 .willReturn(aResponse().withStatus(404)
                                                        .withHeader("Content-Type", "application/json")
                                                        .withBody("""
                                                                  {"error": {"code": 404, "message": "Page token expired"}}
                                                                  """)));

        assertThatThrownBy(() -> platform.files().listSync(
                new io.casehub.connectors.SyncRequest("expired", 100)))
                .isInstanceOf(io.casehub.connectors.SyncTokenExpiredException.class);
    }

}
